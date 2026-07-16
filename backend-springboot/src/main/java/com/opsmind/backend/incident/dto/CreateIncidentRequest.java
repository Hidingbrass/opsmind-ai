package com.opsmind.backend.incident.dto;

import com.opsmind.backend.incident.model.IncidentSeverity;

/**
 * 创建故障事件时的 HTTP 请求体。
 *
 * @param title 故障标题
 * @param serviceName 故障所属服务
 * @param severity 故障严重程度
 * @param symptom 用户或监控系统观察到的现象
 */
public record CreateIncidentRequest(
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom
) {
}
