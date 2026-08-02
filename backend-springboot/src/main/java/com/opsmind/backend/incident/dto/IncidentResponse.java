package com.opsmind.backend.incident.dto;

import java.time.Instant;

import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.model.IncidentSeverity;
import com.opsmind.backend.incident.model.IncidentStatus;

public record IncidentResponse(
        String id,
        String title,
        String serviceName,
        IncidentSeverity severity,
        IncidentStatus status,
        String symptom,
        Instant createdAt,
        Instant updatedAt
) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getSymptom(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
