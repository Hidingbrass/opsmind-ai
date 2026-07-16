package com.opsmind.backend.diagnosis.service;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import com.opsmind.backend.diagnosis.model.DiagnosisRecord;
import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 后台执行一条诊断任务，负责推进状态机、调用诊断服务和发布 SSE 事件。
 */
@Service
public class DiagnosisTaskExecutor {
    /** 读取并保存异步任务状态。 */
    private final DiagnosisTaskRepository diagnosisTaskRepository;
    /** 执行证据收集、AI 调用和报告保存。 */
    private final DiagnosisService diagnosisService;
    /** 将过程和终态通知已订阅的前端。 */
    private final DiagnosisTaskEventPublisher diagnosisTaskEventPublisher;

    /** 由 Spring 注入异步执行需要的协作对象。 */
    public DiagnosisTaskExecutor(
            DiagnosisTaskRepository diagnosisTaskRepository,
            DiagnosisService diagnosisService,
            DiagnosisTaskEventPublisher diagnosisTaskEventPublisher
    ) {
        this.diagnosisTaskRepository = diagnosisTaskRepository;
        this.diagnosisService = diagnosisService;
        this.diagnosisTaskEventPublisher = diagnosisTaskEventPublisher;
    }

    /**
     * 在 Spring 异步线程中执行诊断：先落库 RUNNING，成功后关联报告，失败后保存原因。
     *
     * @param taskId TaskService 刚创建的诊断任务 id
     */
    @Async
    public void execute(String taskId) {
        DiagnosisTask task = diagnosisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("诊断任务不存在: " + taskId));

        // 数据库是任务状态的可靠来源，因此先保存状态，再通过 SSE 通知前端。
        task.markRunning();
        diagnosisTaskRepository.save(task);
        diagnosisTaskEventPublisher.publish(
                taskId,
                DiagnosisTaskEvent.running(taskId, "RUNNING", "诊断任务开始执行")
        );

        try {
            // CALL_AI 是执行过程阶段，不改变任务状态，任务此时仍然是 RUNNING。
            diagnosisTaskEventPublisher.publish(
                    taskId,
                    DiagnosisTaskEvent.running(taskId, "CALL_AI", "正在调用 AI 诊断服务")
            );
            DiagnosisRecord record = diagnosisService.diagnoseAndSaveRecord(task.getIncidentId());
            task.markSuccess(record.getId());
            diagnosisTaskRepository.save(task);
            diagnosisTaskEventPublisher.publish(
                    taskId,
                    DiagnosisTaskEvent.success(taskId, record.getId())
            );
        } catch (Exception ex) {
            String failureReason = ex.getMessage() == null
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            task.markFailed(failureReason);
            diagnosisTaskRepository.save(task);
            diagnosisTaskEventPublisher.publish(
                    taskId,
                    DiagnosisTaskEvent.failed(taskId, failureReason)
            );
        }
    }
}
