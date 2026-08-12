package com.opsmind.backend.fault.dto;

import com.opsmind.backend.incident.dto.IncidentResponse;

/**
 * 故障注入结果，同时告诉调用方使用了哪个场景以及实际创建了哪条 Incident。
 *
 * @param scenarioKey 被注入的场景键
 * @param message 人类可读的注入结果
 * @param incident 新创建的故障事件
 */
public record FaultInjectionResponse(
        String scenarioKey,
        String message,
        IncidentResponse incident
) {
}
