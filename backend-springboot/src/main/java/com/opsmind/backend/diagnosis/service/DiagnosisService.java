package com.opsmind.backend.diagnosis.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.backend.diagnosis.dto.DiagnosisRecordResponse;
import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.diagnosis.model.DiagnosisRecord;
import com.opsmind.backend.diagnosis.repository.DiagnosisRecordRepository;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import com.opsmind.backend.observability.service.ObservabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);
    private static final String PAYMENT_TIMEOUT_TRACE_ID = "trace-payment-timeout-001";

    private final IncidentService incidentService;
    private final ObservabilityService observabilityService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DiagnosisRecordRepository diagnosisRecordRepository;

    public DiagnosisService(
            IncidentService incidentService,
            ObservabilityService observabilityService,
            RestClient restClient,
            ObjectMapper objectMapper,
            DiagnosisRecordRepository diagnosisRecordRepository
    ) {
        this.incidentService = incidentService;
        this.observabilityService = observabilityService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.diagnosisRecordRepository = diagnosisRecordRepository;
    }

    public DiagnosisReport diagnose(String incidentId) {
        Incident incident = incidentService.getById(incidentId);
        IncidentResponse incidentResponse = IncidentResponse.from(incident);
        String serviceName = incident.getServiceName();
        List<LogEntry> logs = observabilityService.queryLogs(serviceName);
        List<MetricPoint> metrics = observabilityService.queryMetrics(serviceName);
        List<TraceSpan> traces = observabilityService.queryTrace(PAYMENT_TIMEOUT_TRACE_ID);
        DiagnosisRequest diagnosisRequest = new DiagnosisRequest(incidentResponse, logs, metrics, traces);
        String requestBody = toJson(diagnosisRequest);
        log.info("调用 AI 诊断服务，incidentId={}, requestBodyLength={}", incidentId, requestBody.length());

        DiagnosisReport body = restClient.post()
                .uri("http://localhost:8000/ai/diagnose")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(DiagnosisReport.class);

        if (body == null) {
            throw new IllegalStateException("AI 服务返回空诊断报告");
        }
        saveDiagnosisRecord(body);
        return body;

    }

    public List<DiagnosisRecordResponse> listRecords(String incidentId) {
        return diagnosisRecordRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DiagnosisRecordResponse toResponse(DiagnosisRecord record) {
        return new DiagnosisRecordResponse(
                record.getId(),
                record.getIncidentId(),
                record.getSummary(),
                record.getRootCause(),
                fromJsonToStringList(record.getEvidenceJson()),
                record.getRecommendation(),
                record.getConfidence(),
                record.getCreatedAt()
        );
    }

    private List<String> fromJsonToStringList(String value) {
        try {
            return objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    private void saveDiagnosisRecord(DiagnosisReport diagnosisReport) {
        String evidenceJson = toJson(diagnosisReport.evidence());
        DiagnosisRecord diagnosisRecord = new DiagnosisRecord(
                diagnosisReport.incidentId(),
                diagnosisReport.summary(),
                diagnosisReport.rootCause(),
                evidenceJson,
                diagnosisReport.recommendation(),
                diagnosisReport.confidence()
        );
        diagnosisRecordRepository.save(diagnosisRecord);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }
}
