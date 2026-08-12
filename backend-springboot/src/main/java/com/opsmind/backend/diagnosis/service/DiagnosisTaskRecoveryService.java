package com.opsmind.backend.diagnosis.service;

import java.util.EnumSet;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** 在单实例服务启动时对账上次进程遗留的未完成诊断任务。 */
@Service
public class DiagnosisTaskRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(DiagnosisTaskRecoveryService.class);
    private static final String RESTART_FAILURE_REASON =
            "服务重启，未完成的诊断任务已终止，请重新发起诊断";

    private final DiagnosisTaskRepository diagnosisTaskRepository;
    private final DiagnosisTaskCacheService diagnosisTaskCacheService;

    public DiagnosisTaskRecoveryService(
            DiagnosisTaskRepository diagnosisTaskRepository,
            DiagnosisTaskCacheService diagnosisTaskCacheService
    ) {
        this.diagnosisTaskRepository = diagnosisTaskRepository;
        this.diagnosisTaskCacheService = diagnosisTaskCacheService;
    }

    /** Spring 完成启动后终止上次进程无法继续执行的任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        int recovered = recoverInterruptedTasks();
        if (recovered > 0) {
            log.warn("服务启动时已终止 {} 个遗留的未完成诊断任务", recovered);
        }
    }

    /**
     * 将数据库中的旧 PENDING/RUNNING 任务置为失败，同时清理 Redis 复用状态。
     *
     * <p>本地单实例进程无法安全续跑旧的内存异步任务。这里明确失败而不自动重放，
     * 避免重复生成诊断记录或工具审计数据。
     *
     * @return 本次启动成功对账的任务数
     */
    int recoverInterruptedTasks() {
        int recovered = 0;
        for (DiagnosisTask task : diagnosisTaskRepository.findAllByStatusIn(
                EnumSet.of(DiagnosisTaskStatus.PENDING, DiagnosisTaskStatus.RUNNING))) {
            try {
                task.markFailed(RESTART_FAILURE_REASON);
                DiagnosisTask savedTask = diagnosisTaskRepository.saveAndFlush(task);
                diagnosisTaskCacheService.putTask(DiagnosisTaskResponse.from(savedTask));
                diagnosisTaskCacheService.finishTask(savedTask.getIncidentId(), false);
                recovered++;
            } catch (RuntimeException exception) {
                log.error("遗留诊断任务对账失败: taskId={}", task.getId(), exception);
            }
        }
        return recovered;
    }
}
