package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import com.opsmind.backend.diagnosis.dto.AgentExecutionMetadata;
import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.diagnosis.model.AiCallAudit;
import com.opsmind.backend.diagnosis.repository.AiCallAuditRepository;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.IncidentSeverity;
import com.opsmind.backend.incident.model.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiCallAuditServiceTest {

    @Test
    void successfulLlmCallPersistsModelAndUsageMetadata() {
        AiCallAuditRepository repository = mock(AiCallAuditRepository.class);
        AiCallAuditService service = new AiCallAuditService(repository);
        Instant now = Instant.parse("2026-07-05T10:00:00Z");
        IncidentResponse incident = new IncidentResponse(
                "incident-1",
                "支付超时",
                "payment-service",
                IncidentSeverity.HIGH,
                IncidentStatus.OPEN,
                "支付接口超时",
                now,
                now
        );
        DiagnosisRequest request = new DiagnosisRequest(
                "task-1",
                "0123456789abcdef0123456789abcdef",
                incident,
                List.of(),
                List.of(),
                List.of()
        );
        DiagnosisReport report = new DiagnosisReport(
                "incident-1",
                "0123456789abcdef0123456789abcdef",
                "summary",
                "root cause",
                List.of("evidence"),
                "recommendation",
                0.8,
                new AgentExecutionMetadata(
                        "LLM",
                        "openai-compatible",
                        "test-model",
                        "prompt-v1",
                        120,
                        45,
                        4
                )
        );

        service.record(request, report, true, 300, null);

        ArgumentCaptor<AiCallAudit> captor = ArgumentCaptor.forClass(AiCallAudit.class);
        verify(repository).save(captor.capture());
        AiCallAudit audit = captor.getValue();
        assertThat(audit.getExecutionMode()).isEqualTo("LLM");
        assertThat(audit.getModelName()).isEqualTo("test-model");
        assertThat(audit.getPromptVersion()).isEqualTo("prompt-v1");
        assertThat(audit.getInputTokens()).isEqualTo(120);
        assertThat(audit.getOutputTokens()).isEqualTo(45);
        assertThat(audit.getAgentToolCallCount()).isEqualTo(4);
    }
}
