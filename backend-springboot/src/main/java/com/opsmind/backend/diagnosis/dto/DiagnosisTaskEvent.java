package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;

import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;

/**
 * 通过 SSE 发给前端的瞬时任务事件，它不入库，数据库中的 DiagnosisTask 才是最终状态依据。
 *
 * @param taskId 事件所属诊断任务
 * @param status 任务当前状态
 * @param stage 用作 SSE event name 的细分执行阶段
 * @param message 前端时间线可展示的文本
 * @param diagnosisRecordId 成功终态携带的报告 id
 * @param failureReason 失败终态携带的原因
 * @param timestamp 事件创建时间
 */
public record DiagnosisTaskEvent(
        String taskId,
        DiagnosisTaskStatus status,
        String stage,
        String message,
        String diagnosisRecordId,
        String failureReason,
        Instant timestamp
) {
    /** @return 新任务尚未被异步线程取走时的快照事件 */
    public static DiagnosisTaskEvent pending(String taskId) {
        return new DiagnosisTaskEvent(
                taskId,
                DiagnosisTaskStatus.PENDING,
                "PENDING",
                "诊断任务等待执行",
                null,
                null,
                Instant.now()
        );
    }

    /**
     * @param taskId 诊断任务 id
     * @param stage RUNNING、CALL_AI 等过程阶段
     * @param message 阶段说明
     * @return 保持 SSE 连接的运行中事件
     */
    public static DiagnosisTaskEvent running(String taskId, String stage, String message) {
        return new DiagnosisTaskEvent(
                taskId,
                DiagnosisTaskStatus.RUNNING,
                stage,
                message,
                null,
                null,
                Instant.now()
        );
    }

    /** @return 携带最终报告 id 的成功终态事件 */
    public static DiagnosisTaskEvent success(String taskId, String diagnosisRecordId) {
        return new DiagnosisTaskEvent(
                taskId,
                DiagnosisTaskStatus.SUCCESS,
                "SUCCESS",
                "诊断任务执行成功",
                diagnosisRecordId,
                null,
                Instant.now()
        );
    }

    /** @return 携带失败原因的失败终态事件 */
    public static DiagnosisTaskEvent failed(String taskId, String failureReason) {
        return new DiagnosisTaskEvent(
                taskId,
                DiagnosisTaskStatus.FAILED,
                "FAILED",
                "诊断任务执行失败",
                null,
                failureReason,
                Instant.now()
        );
    }
}
