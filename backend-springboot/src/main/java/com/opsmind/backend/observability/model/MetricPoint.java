package com.opsmind.backend.observability.model;

import java.time.Instant;

public record MetricPoint(
        Instant timestamp,
        String serviceName,
        String metricName,
        double value,
        String unit
) {
}
