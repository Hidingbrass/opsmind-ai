package com.opsmind.backend.fault.dto;

import com.opsmind.backend.incident.dto.IncidentResponse;

public record FaultInjectionResponse(
        String scenarioKey,
        String message,
        IncidentResponse incident
) {
}
