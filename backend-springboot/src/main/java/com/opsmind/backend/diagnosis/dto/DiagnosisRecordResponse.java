package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;
import java.util.List;

public record DiagnosisRecordResponse(
        String id,
        String incidentId,
        String summary,
        String rootCause,
        List<String> evidence,
        String recommendation,
        double confidence,
        Instant createdAt
) {
}