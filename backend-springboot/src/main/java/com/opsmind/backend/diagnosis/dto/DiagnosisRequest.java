package com.opsmind.backend.diagnosis.dto;

import java.util.List;

import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;

/**
 * Spring Boot 发送给 Python AI 服务的诊断上下文。
 *
 * @param incident 故障本身的标题、服务和现象
 * @param logs 相关服务日志
 * @param metrics 相关监控指标
 * @param traces 通过日志 traceId 关联得到的链路节点
 */
public record DiagnosisRequest(
        IncidentResponse incident,
        List<LogEntry> logs,
        List<MetricPoint> metrics,
        List<TraceSpan> traces
) {
}
