package com.opsmind.backend.tool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.dto.IncidentReportResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskEventPublisher;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskService;
import com.opsmind.backend.diagnosis.service.DiagnosisService;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.DeploymentRecord;
import com.opsmind.backend.observability.model.TraceSpan;
import com.opsmind.backend.observability.service.ObservabilityService;
import com.opsmind.backend.observability.service.OpsMindMetrics;
import com.opsmind.backend.tool.dto.ToolExecutionRequest;
import com.opsmind.backend.tool.dto.ToolExecutionResponse;
import com.opsmind.backend.tool.dto.ToolExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ToolGatewayServiceTest {

    private DiagnosisTaskService diagnosisTaskService;
    private DiagnosisService diagnosisService;
    private IncidentService incidentService;
    private ObservabilityService observabilityService;
    private ToolCallAuditService toolCallAuditService;
    private DiagnosisTaskEventPublisher eventPublisher;
    private ToolGatewayService toolGatewayService;

    @BeforeEach
    void setUp() {
        diagnosisTaskService = mock(DiagnosisTaskService.class);
        diagnosisService = mock(DiagnosisService.class);
        incidentService = mock(IncidentService.class);
        observabilityService = mock(ObservabilityService.class);
        toolCallAuditService = mock(ToolCallAuditService.class);
        eventPublisher = mock(DiagnosisTaskEventPublisher.class);
        toolGatewayService = new ToolGatewayService(
                diagnosisTaskService,
                diagnosisService,
                incidentService,
                observabilityService,
                toolCallAuditService,
                eventPublisher,
                mock(RestClient.class),
                "http://localhost:8000",
                mock(OpsMindMetrics.class)
        );

        when(diagnosisTaskService.getTask("task-1"))
                .thenReturn(task("task-1", "incident-1"));
        Incident incident = mock(Incident.class);
        when(incident.getId()).thenReturn("incident-1");
        when(incident.getServiceName()).thenReturn("payment-service");
        when(incidentService.getById("incident-1")).thenReturn(incident);
    }

    @Test
    void queryMetricsUsesWhitelistedObservabilityMethod() {
        List<MetricPoint> metrics = List.of(new MetricPoint(
                Instant.parse("2026-07-05T10:00:00Z"),
                "payment-service",
                "http.server.requests.p95",
                5200,
                "ms"
        ));
        when(observabilityService.queryMetrics("payment-service")).thenReturn(metrics);

        ToolExecutionRequest request = request(
                "queryMetrics",
                Map.of("serviceName", "payment-service")
        );
        ToolExecutionResponse response = toolGatewayService.execute(request);

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(response.data()).isEqualTo(metrics);
        verify(observabilityService).queryMetrics("payment-service");
        verify(toolCallAuditService).record(
                request,
                response,
                "0123456789abcdef0123456789abcdef"
        );
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce())
                .publish(org.mockito.Mockito.eq("task-1"), any());
    }

    @Test
    void queryTraceUsesTraceIdArgument() {
        List<TraceSpan> spans = List.of(new TraceSpan(
                "trace-1",
                "span-1",
                null,
                "payment-service",
                "POST /pay",
                5000,
                "ERROR",
                "timeout"
        ));
        when(observabilityService.queryTrace("trace-1")).thenReturn(spans);
        when(observabilityService.queryLogs("payment-service")).thenReturn(List.of(
                new LogEntry(
                        Instant.parse("2026-07-05T10:00:00Z"),
                        "payment-service",
                        "ERROR",
                        "trace-1",
                        "timeout"
                )
        ));

        ToolExecutionResponse response = toolGatewayService.execute(
                request("queryTrace", Map.of("traceId", "trace-1"))
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(response.data()).isEqualTo(spans);
        verify(observabilityService).queryTrace("trace-1");
    }

    @Test
    void crossServiceQueryBecomesStructuredFailure() {
        ToolExecutionResponse response = toolGatewayService.execute(
                request("queryLogs", Map.of("serviceName", "admin-service"))
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(response.errorMessage()).contains("不属于当前故障");
    }

    @Test
    void crossIncidentTraceBecomesStructuredFailure() {
        when(observabilityService.queryLogs("payment-service")).thenReturn(List.of());

        ToolExecutionResponse response = toolGatewayService.execute(
                request("queryTrace", Map.of("traceId", "unrelated-trace"))
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(response.errorMessage()).contains("traceId");
    }

    @Test
    void invalidMetricArgumentsBecomeStructuredFailure() {
        ToolExecutionResponse response = toolGatewayService.execute(
                request("queryMetrics", Map.of("serviceName", " "))
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(response.errorMessage()).contains("serviceName");
        verify(toolCallAuditService).record(any(), any(), any());
    }

    @Test
    void getRecentDeploymentsUsesWhitelistedObservabilityMethod() {
        List<DeploymentRecord> deployments = List.of(new DeploymentRecord(
                Instant.parse("2026-07-05T08:30:00Z"),
                "payment-service",
                "2.4.1",
                "8fd31a2",
                "release-bot",
                "SUCCESS",
                "调整支付网关参数"
        ));
        when(observabilityService.getRecentDeployments("payment-service"))
                .thenReturn(deployments);

        ToolExecutionResponse response = toolGatewayService.execute(
                request(
                        "getRecentDeployments",
                        Map.of("serviceName", "payment-service")
                )
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(response.data()).isEqualTo(deployments);
        verify(observabilityService).getRecentDeployments("payment-service");
    }

    @Test
    void generateIncidentReportRejectsCrossIncidentAccess() {
        ToolExecutionResponse response = toolGatewayService.execute(
                request(
                        "generateIncidentReport",
                        Map.of("incidentId", "another-incident")
                )
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(response.errorMessage()).contains("不一致");
    }

    @Test
    void generateIncidentReportUsesDiagnosisServiceForSameIncident() {
        IncidentReportResponse report = new IncidentReportResponse(
                "incident-1",
                "事故复盘",
                "HIGH 级故障",
                "支付网关超时",
                List.of("故障创建"),
                List.of("切换备用通道"),
                Instant.parse("2026-07-05T10:10:00Z")
        );
        when(diagnosisService.generateIncidentReport("incident-1"))
                .thenReturn(report);

        ToolExecutionResponse response = toolGatewayService.execute(
                request(
                        "generateIncidentReport",
                        Map.of("incidentId", "incident-1")
                )
        );

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(response.data()).isEqualTo(report);
        verify(diagnosisService).generateIncidentReport("incident-1");
    }

    private ToolExecutionRequest request(String toolName, Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "task-1",
                "incident-1",
                toolName,
                arguments
        );
    }

    private DiagnosisTaskResponse task(String taskId, String incidentId) {
        Instant now = Instant.parse("2026-07-05T10:00:00Z");
        return new DiagnosisTaskResponse(
                taskId,
                incidentId,
                "0123456789abcdef0123456789abcdef",
                DiagnosisTaskStatus.RUNNING,
                null,
                null,
                now,
                now,
                now,
                null
        );
    }
}
