package com.opsmind.backend.diagnosis.service;

import java.util.List;
import java.util.Optional;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.service.OpsMindMetrics;
import com.opsmind.backend.observability.service.TraceContextService;
import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 面向 Controller 的诊断任务编排服务，负责创建/查询任务和建立 SSE 订阅。
 *
 * <p>真正的后台执行由 DiagnosisTaskExecutor 负责，连接管理由 DiagnosisTaskEventPublisher 负责。
 */
@Service
public class DiagnosisTaskService {
    /** 用于校验诊断任务引用的故障真实存在。 */
    private final IncidentService incidentService;
    /** 诊断任务持久化仓库。 */
    private final DiagnosisTaskRepository diagnosisTaskRepository;
    /** 将任务交给异步线程执行的后台执行器。 */
    private final DiagnosisTaskExecutor diagnosisTaskExecutor;
    /** 创建 SSE 连接并推送任务事件的发布器。 */
    private final DiagnosisTaskEventPublisher diagnosisTaskEventPublisher;
    /** Redis 任务状态、结果复用和分布式去重能力。 */
    private final DiagnosisTaskCacheService diagnosisTaskCacheService;
    /** 按客户端限制高成本诊断任务的创建频率。 */
    private final DiagnosisRequestRateLimiter diagnosisRequestRateLimiter;
    /** 记录诊断任务创建数量。 */
    private final OpsMindMetrics opsMindMetrics;
    /** 从当前 OpenTelemetry Span 获取要持久化的 traceId。 */
    private final TraceContextService traceContextService;

    /** 由 Spring 注入任务编排所需依赖。 */
    public DiagnosisTaskService(
            IncidentService incidentService,
            DiagnosisTaskRepository diagnosisTaskRepository,
            DiagnosisTaskExecutor diagnosisTaskExecutor,
            DiagnosisTaskEventPublisher diagnosisTaskEventPublisher,
            DiagnosisTaskCacheService diagnosisTaskCacheService,
            DiagnosisRequestRateLimiter diagnosisRequestRateLimiter,
            OpsMindMetrics opsMindMetrics,
            TraceContextService traceContextService
    ) {
        this.incidentService = incidentService;
        this.diagnosisTaskRepository = diagnosisTaskRepository;
        this.diagnosisTaskExecutor = diagnosisTaskExecutor;
        this.diagnosisTaskEventPublisher = diagnosisTaskEventPublisher;
        this.diagnosisTaskCacheService = diagnosisTaskCacheService;
        this.diagnosisRequestRateLimiter = diagnosisRequestRateLimiter;
        this.opsMindMetrics = opsMindMetrics;
        this.traceContextService = traceContextService;
    }

    /**
     * 创建 PENDING 任务、提交异步执行，然后立即返回 taskId。
     *
     * @param incidentId 要诊断的故障 id
     * @return 刚刚入库的任务快照
     */
    public DiagnosisTaskResponse createTask(String incidentId, String clientKey) {
        // 先校验故障事件存在，避免创建后续无法执行的孤儿诊断任务。
        incidentService.getById(incidentId);

        Optional<DiagnosisTaskResponse> reusable = findReusableTask(incidentId);
        if (reusable.isPresent()) {
            return reusable.get();
        }
        // 缓存命中不会触发新 AI 调用，只有真正准备创建任务时才消耗限流额度。
        diagnosisRequestRateLimiter.check(clientKey);

        boolean lockAcquired = diagnosisTaskCacheService.tryAcquireIncidentLock(incidentId);
        if (!lockAcquired) {
            return waitForConcurrentTask(incidentId)
                    .orElseThrow(() -> new DiagnosisTaskConflictException(
                            "相同故障的诊断任务正在创建，请稍后重试"
                    ));
        }

        DiagnosisTask savedTask = null;
        try {
            DiagnosisTask diagnosisTask = new DiagnosisTask(
                    incidentId,
                    traceContextService.currentTraceId()
            );
            savedTask = diagnosisTaskRepository.save(diagnosisTask);
            DiagnosisTaskResponse response = DiagnosisTaskResponse.from(savedTask);
            diagnosisTaskCacheService.putTask(response);
            diagnosisTaskCacheService.rememberTask(incidentId, response.id());
            opsMindMetrics.taskCreated();
            diagnosisTaskExecutor.execute(savedTask.getId());
            return response;
        } catch (TaskRejectedException exception) {
            if (savedTask != null) {
                markSubmissionRejected(savedTask);
            } else {
                diagnosisTaskCacheService.finishTask(incidentId, false);
            }
            throw exception;
        } catch (RuntimeException exception) {
            diagnosisTaskCacheService.finishTask(incidentId, false);
            throw exception;
        }
    }

    /** 兼容内部调用；没有 HTTP 客户端上下文时使用固定限流键。 */
    public DiagnosisTaskResponse createTask(String incidentId) {
        return createTask(incidentId, "internal");
    }

    /** 提交被拒绝时先写入可恢复终态，避免数据库遗留永久 PENDING 任务。 */
    private void markSubmissionRejected(DiagnosisTask task) {
        String failureReason = "诊断执行容量已满，请稍后重试";
        task.markFailed(failureReason);
        DiagnosisTask failedTask = diagnosisTaskRepository.saveAndFlush(task);
        diagnosisTaskCacheService.putTask(DiagnosisTaskResponse.from(failedTask));
        diagnosisTaskCacheService.finishTask(failedTask.getIncidentId(), false);
        opsMindMetrics.taskFinished(false, java.time.Duration.ZERO);
        diagnosisTaskEventPublisher.publish(
                failedTask.getId(),
                DiagnosisTaskEvent.failed(failedTask.getId(), failureReason)
        );
    }

    /** @return 指定 taskId 的缓存或数据库状态快照 */
    public DiagnosisTaskResponse getTask(String taskId) {
        Optional<DiagnosisTaskResponse> cached = diagnosisTaskCacheService.getTask(taskId);
        if (cached.isPresent()) {
            return cached.get();
        }

        DiagnosisTask task = diagnosisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在: " + taskId));
        DiagnosisTaskResponse response = DiagnosisTaskResponse.from(task);
        diagnosisTaskCacheService.putTask(response);
        return response;
    }

    /**
     * 查询某个故障最近一次诊断任务，供页面刷新后恢复状态。
     *
     * @param incidentId 故障 id
     * @return 最近任务；从未诊断过时返回空
     */
    public Optional<DiagnosisTaskResponse> getLatestTaskForIncident(String incidentId) {
        incidentService.getById(incidentId);
        return diagnosisTaskRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .map(DiagnosisTaskResponse::from);
    }

    /**
     * 为任务建立 SSE 连接，并立即推送一次当前数据库状态快照。
     *
     * @return 交给 Spring MVC 保持的长连接
     */
    public SseEmitter subscribeEvents(String taskId) {
        // 先确认任务真实存在，避免为无效 taskId 创建无意义的长连接。
        diagnosisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在: " + taskId));

        // 先注册连接，确保从这一刻开始的状态变化都能被 Publisher 捕获。
        SseEmitter sseEmitter = diagnosisTaskEventPublisher.subscribe(taskId);

        // 注册连接后重新查询，获取尽可能新的数据库状态作为订阅快照。
        DiagnosisTask currentTask = diagnosisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在: " + taskId));

        diagnosisTaskEventPublisher.sendSnapshot(
                taskId,
                sseEmitter,
                toCurrentEvent(currentTask)
        );
        return sseEmitter;
    }

    /** 根据持久化任务状态构建对应的 SSE 快照事件。 */
    private DiagnosisTaskEvent toCurrentEvent(DiagnosisTask task) {
        return switch (task.getStatus()) {
            case PENDING -> DiagnosisTaskEvent.pending(task.getId());
            case RUNNING -> DiagnosisTaskEvent.running(
                    task.getId(),
                    "RUNNING",
                    "诊断任务正在执行"
            );
            case SUCCESS -> DiagnosisTaskEvent.success(
                    task.getId(),
                    task.getDiagnosisRecordId()
            );
            case FAILED -> DiagnosisTaskEvent.failed(
                    task.getId(),
                    task.getFailureReason()
            );
        };
    }

    /** 根据 Redis 中的 incident -> task 索引确认任务仍可复用。 */
    private Optional<DiagnosisTaskResponse> findReusableTask(String incidentId) {
        Optional<String> cachedTaskId = diagnosisTaskCacheService.getReusableTaskId(incidentId);
        if (cachedTaskId.isEmpty()) {
            return Optional.empty();
        }

        Optional<DiagnosisTask> task = diagnosisTaskRepository.findById(cachedTaskId.get());
        if (task.isEmpty() || task.get().getStatus() == DiagnosisTaskStatus.FAILED) {
            diagnosisTaskCacheService.finishTask(incidentId, false);
            return Optional.empty();
        }

        DiagnosisTaskResponse response = DiagnosisTaskResponse.from(task.get());
        diagnosisTaskCacheService.putTask(response);
        return Optional.of(response);
    }

    /**
     * 另一个请求刚拿到 Redis 锁时，它可能尚未来得及写入 MySQL；短暂轮询可封住这个
     * 并发窗口，同时避免当前请求绕过锁再创建一条重复任务。
     */
    private Optional<DiagnosisTaskResponse> waitForConcurrentTask(String incidentId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<String> cachedTaskId = diagnosisTaskCacheService
                    .getReusableTaskId(incidentId);
            if (cachedTaskId.isPresent()) {
                Optional<DiagnosisTask> cachedTask = diagnosisTaskRepository
                        .findById(cachedTaskId.get());
                if (cachedTask.isPresent()) {
                    return Optional.of(DiagnosisTaskResponse.from(cachedTask.get()));
                }
            }

            Optional<DiagnosisTask> runningTask = diagnosisTaskRepository
                    .findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                            incidentId,
                            List.of(DiagnosisTaskStatus.PENDING, DiagnosisTaskStatus.RUNNING)
                    );
            if (runningTask.isPresent()) {
                DiagnosisTaskResponse response = DiagnosisTaskResponse.from(runningTask.get());
                diagnosisTaskCacheService.putTask(response);
                diagnosisTaskCacheService.rememberTask(incidentId, response.id());
                return Optional.of(response);
            }

            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
