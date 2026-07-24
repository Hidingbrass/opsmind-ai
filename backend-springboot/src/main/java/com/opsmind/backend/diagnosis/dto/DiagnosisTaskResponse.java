package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;

import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;

/**
 * 创建或查询异步诊断任务时返回的持久化状态快照。
 *
 * @param id 前端查询状态和订阅 SSE 使用的 taskId
 * @param incidentId 该任务诊断的故障 id
 * @param traceId 串联入口、异步任务、工具和 AI 调用的链路 id
 * @param status 任务持久化状态
 * @param diagnosisRecordId 成功后关联的最终报告 id
 * @param failureReason 失败时保存的可读原因
 * @param createdAt 任务创建时间
 * @param updatedAt 最后状态更新时间
 * @param startedAt 后台线程开始执行时间
 * @param finishedAt 成功或失败的终止时间
 */
public record DiagnosisTaskResponse(
        String id,
        String incidentId,
        String traceId,
        DiagnosisTaskStatus status,
        String diagnosisRecordId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt
) {
    /**
     * 将 JPA 任务实体转换为不带持久化行为的 API DTO。
     *
     * @param task 诊断任务实体
     * @return 当前任务快照
     */
    public static DiagnosisTaskResponse from(DiagnosisTask task) {
        return new DiagnosisTaskResponse(
                task.getId(),
                task.getIncidentId(),
                task.getTraceId(),
                task.getStatus(),
                task.getDiagnosisRecordId(),
                task.getFailureReason(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }
}
