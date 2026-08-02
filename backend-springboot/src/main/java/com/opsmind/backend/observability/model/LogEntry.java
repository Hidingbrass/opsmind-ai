package com.opsmind.backend.observability.model;

import java.time.Instant;

public record LogEntry(
        Instant timestamp,
        String serviceName,
        String level,
        String traceId,
        String message
) {

}
