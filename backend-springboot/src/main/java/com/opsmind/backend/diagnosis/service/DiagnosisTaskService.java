package com.opsmind.backend.diagnosis.service;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import com.opsmind.backend.incident.service.IncidentService;
import org.springframework.stereotype.Service;
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

    /** 由 Spring 注入任务编排所需依赖。 */
    public DiagnosisTaskService(
            IncidentService incidentService,
            DiagnosisTaskRepository diagnosisTaskRepository,
            DiagnosisTaskExecutor diagnosisTaskExecutor,
            DiagnosisTaskEventPublisher diagnosisTaskEventPublisher
    ) {
        this.incidentService = incidentService;
        this.diagnosisTaskRepository = diagnosisTaskRepository;
        this.diagnosisTaskExecutor = diagnosisTaskExecutor;
        this.diagnosisTaskEventPublisher = diagnosisTaskEventPublisher;
    }

    /**
     * 创建 PENDING 任务、提交异步执行，然后立即返回 taskId。
     *
     * @param incidentId 要诊断的故障 id
     * @return 刚刚入库的任务快照
     */
    public DiagnosisTaskResponse createTask(String incidentId) {
        // 先校验故障事件存在，避免创建后续无法执行的孤儿诊断任务。
        incidentService.getById(incidentId);

        DiagnosisTask diagnosisTask = new DiagnosisTask(incidentId);
        DiagnosisTask savedTask = diagnosisTaskRepository.save(diagnosisTask);
        diagnosisTaskExecutor.execute(savedTask.getId());
        return DiagnosisTaskResponse.from(savedTask);
    }

    /** @return 指定 taskId 的数据库状态快照 */
    public DiagnosisTaskResponse getTask(String taskId) {
        DiagnosisTask task = diagnosisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在: " + taskId));
        return DiagnosisTaskResponse.from(task);
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

        diagnosisTaskEventPublisher.publish(taskId, toCurrentEvent(currentTask));
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
}
