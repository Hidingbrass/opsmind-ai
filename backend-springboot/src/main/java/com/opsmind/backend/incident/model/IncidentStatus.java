package com.opsmind.backend.incident.model;

/** 故障事件生命周期状态。 */
public enum IncidentStatus {
    /** 事件已创建，尚未完成诊断。 */
    OPEN,
    /** 后台正在收集证据并执行 AI 诊断。 */
    DIAGNOSING,
    /** 故障已处理并确认恢复。 */
    RESOLVED
}
