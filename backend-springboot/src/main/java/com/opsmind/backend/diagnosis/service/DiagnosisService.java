package com.opsmind.backend.diagnosis.service;

import java.util.List;

import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import com.opsmind.backend.observability.service.ObservabilityService;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {

    private static final String PAYMENT_TIMEOUT_TRACE_ID = "trace-payment-timeout-001";

    private final IncidentService incidentService;
    private final ObservabilityService observabilityService;

    public DiagnosisService(IncidentService incidentService, ObservabilityService observabilityService) {
        this.incidentService = incidentService;
        this.observabilityService = observabilityService;
    }

    public DiagnosisReport diagnose(String incidentId) {
        Incident incident = incidentService.getById(incidentId);
        IncidentResponse incidentResponse = IncidentResponse.from(incident);
        String serviceName = incident.getServiceName();
        List<LogEntry> logs = observabilityService.queryLogs(serviceName);
        List<MetricPoint> metrics = observabilityService.queryMetrics(serviceName);
        List<TraceSpan> traces = observabilityService.queryTrace(PAYMENT_TIMEOUT_TRACE_ID);
        DiagnosisRequest diagnosisRequest = new DiagnosisRequest(incidentResponse, logs, metrics, traces);

        return new DiagnosisReport(
                incidentId,
                "检测到 " + diagnosisRequest.incident().serviceName() + " 存在异常，故障与支付链路超时高度相关。",
                "支付服务调用第三方支付网关超时，导致订单结算请求失败。",
                List.of(
                        "日志中出现调用第三方支付网关超时",
                        "payment-service 的 P95 延迟升高到 5200ms",
                        "链路追踪显示 payment-service -> payment-gateway 调用耗时 5000ms 并返回 ERROR"
                ),
                "建议检查第三方支付网关状态，必要时切换备用支付通道或启用支付降级策略。",
                0.86
        );
    }
}
