package com.opsmind.backend.diagnosis.dto;

import java.util.List;

public record DiagnosisReport(
        String incidentId,
        String summary,
        String rootCause,
        List<String> evidence,
        String recommendation,
        double confidence
) {
}