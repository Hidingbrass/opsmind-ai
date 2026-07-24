package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;

import com.opsmind.backend.diagnosis.model.AiCallAudit;
import com.opsmind.backend.diagnosis.model.AiCallStatus;

/** 前端可查询的 AI 调用审计视图，不包含异常堆栈或请求正文。 */
public record AiCallAuditResponse(
        String id,
        String taskId,
        String incidentId,
        String traceId,
        String provider,
        String modelName,
        AiCallStatus status,
        long latencyMs,
        String errorMessage,
        Instant createdAt
) {
    /** 将 JPA 实体转换成不含请求正文和异常堆栈的对外 DTO。 */
    public static AiCallAuditResponse from(AiCallAudit audit) {
        return new AiCallAuditResponse(
                audit.getId(),
                audit.getTaskId(),
                audit.getIncidentId(),
                audit.getTraceId(),
                audit.getProvider(),
                audit.getModelName(),
                audit.getStatus(),
                audit.getLatencyMs(),
                audit.getErrorMessage(),
                audit.getCreatedAt()
        );
    }
}
