package com.opsmind.backend.incident.model;

/** 故障影响等级，用于排序处置优先级和展示告警级别。 */
public enum IncidentSeverity {
    /** 低影响，不影响核心链路。 */
    LOW,
    /** 中等影响，部分能力受损但有替代路径。 */
    MEDIUM,
    /** 高影响，核心用户链路出现明显故障。 */
    HIGH,
    /** 严重影响，需要立即响应的系统级故障。 */
    CRITICAL
}
