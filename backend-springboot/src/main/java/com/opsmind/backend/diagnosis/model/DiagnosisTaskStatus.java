package com.opsmind.backend.diagnosis.model;

/** 异步诊断任务的持久化状态机。 */
public enum DiagnosisTaskStatus {
    /** 任务已创建，还没开始执行。 */
    PENDING,

    /** 后台线程正在调用 AI 服务并保存诊断结果。 */
    RUNNING,

    /** 诊断成功，最终 DiagnosisRecord 已保存。 */
    SUCCESS,

    /** 诊断失败，failureReason 中保存失败原因。 */
    FAILED
}
