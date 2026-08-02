package com.opsmind.backend.diagnosis.dto;

import java.util.List;

import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;

public record DiagnosisRequest(
        IncidentResponse incident,
        List<LogEntry> logs,
        List<MetricPoint> metrics,
        List<TraceSpan> traces
) {
}
