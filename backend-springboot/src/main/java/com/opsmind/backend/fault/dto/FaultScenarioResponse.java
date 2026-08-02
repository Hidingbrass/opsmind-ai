package com.opsmind.backend.fault.dto;

import com.opsmind.backend.fault.model.FaultScenario;
import com.opsmind.backend.incident.model.IncidentSeverity;

public record FaultScenarioResponse(
        String key,
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom
) {

    public static FaultScenarioResponse from(FaultScenario scenario) {
        return new FaultScenarioResponse(
                scenario.key(),
                scenario.title(),
                scenario.serviceName(),
                scenario.severity(),
                scenario.symptom()
        );
    }
}
