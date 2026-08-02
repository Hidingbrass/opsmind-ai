package com.opsmind.backend.observability.model;

public record TraceSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String serviceName,
        String operationName,
        long durationMs,
        String status,
        String errorMessage
) {
}
