package com.opsmind.backend.fault.model;

import com.opsmind.backend.incident.model.IncidentSeverity;

public record FaultScenario(
        String key,
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom,
        String expectedRootCause,
        String suggestedAction
) {
}