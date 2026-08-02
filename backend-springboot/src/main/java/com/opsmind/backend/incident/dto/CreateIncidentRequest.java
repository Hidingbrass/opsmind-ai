package com.opsmind.backend.incident.dto;

import com.opsmind.backend.incident.model.IncidentSeverity;

public record CreateIncidentRequest(
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom
) {
}
