package com.opsmind.backend.incident;

public record CreateIncidentRequest(
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom
) {
}
