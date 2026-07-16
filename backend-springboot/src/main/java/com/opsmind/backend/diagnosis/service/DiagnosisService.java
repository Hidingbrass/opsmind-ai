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

/**
 * 诊断核心服务：收集故障上下文，调用 Python AI 服务，并将结构化结果持久化。
 *
 * <p>它不管理异步线程和 SSE，因此同步 Controller 与异步 TaskExecutor 都能复用同一诊断能力。
 */
@Service
public class DiagnosisService {

    /** 记录 AI 调用上下文大小等运行信息。 */
    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

    /** 查询待诊断故障的业务服务。 */
    private final IncidentService incidentService;
    /** 收集日志、指标和 Trace 的查询服务。 */
    private final ObservabilityService observabilityService;
    /** 调用 FastAPI AI 服务的 HTTP 客户端。 */
    private final RestClient restClient;
    /** Java 对象与 JSON 之间的序列化工具。 */
    private final ObjectMapper objectMapper;
    /** 诊断报告持久化仓库。 */
    private final DiagnosisRecordRepository diagnosisRecordRepository;

    /** 由 Spring 构造器注入诊断链路需要的所有协作对象。 */
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

    /**
     * 供同步 HTTP 接口使用，返回不含数据库内部字段的诊断报告。
     *
     * @param incidentId 故障 id
     * @return 已完成并保存的诊断结果
     */
    public DiagnosisReport diagnose(String incidentId) {
        DiagnosisRecord savedRecord = diagnoseAndSaveRecord(incidentId);
        return toReport(savedRecord);
    }

    /**
     * 供异步执行器使用，返回已保存实体，便于任务记录 diagnosisRecordId。
     *
     * @param incidentId 故障 id
     * @return 已生成数据库 id 的诊断记录
     */
    public DiagnosisRecord diagnoseAndSaveRecord(String incidentId) {
        DiagnosisReport report = requestAiDiagnosis(incidentId);
        return saveDiagnosisRecord(report);
    }

    /** 组装完整故障上下文并调用 {@code POST /ai/diagnose}。 */
    private DiagnosisReport requestAiDiagnosis(String incidentId) {
        Incident incident = incidentService.getById(incidentId);
        IncidentResponse incidentResponse = IncidentResponse.from(incident);
        String serviceName = incident.getServiceName();
        List<LogEntry> logs = observabilityService.queryLogs(serviceName);
        List<MetricPoint> metrics = observabilityService.queryMetrics(serviceName);
        // 先从日志提取 traceId，再查询链路，避免向 AI 发送与当前故障无关的 Span。
        List<TraceSpan> traces = logs.stream()
                .map(LogEntry::traceId)
                .distinct()
                .flatMap(traceId -> observabilityService.queryTrace(traceId).stream())
                .toList();

        // 把故障事件、日志、指标、链路追踪统一打包给 AI 服务，AI 才能基于完整上下文做诊断。
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
        return body;
    }

    /** @return 按时间倒序排列的某故障诊断历史 */
    public List<DiagnosisRecordResponse> listRecords(String incidentId) {
        return diagnosisRecordRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 将数据库记录转换为历史查询 DTO。 */
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

    /** 将数据库记录还原为同步诊断接口的报告格式。 */
    private DiagnosisReport toReport(DiagnosisRecord record) {
        return new DiagnosisReport(
                record.getIncidentId(),
                record.getSummary(),
                record.getRootCause(),
                fromJsonToStringList(record.getEvidenceJson()),
                record.getRecommendation(),
                record.getConfidence()
        );
    }

    /** 将数据库中的证据 JSON 还原为字符串列表。 */
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

    /** 将 AI 返回的报告转换为 JPA 实体并保存。 */
    private DiagnosisRecord saveDiagnosisRecord(DiagnosisReport diagnosisReport) {
        String evidenceJson = toJson(diagnosisReport.evidence());
        DiagnosisRecord diagnosisRecord = new DiagnosisRecord(
                diagnosisReport.incidentId(),
                diagnosisReport.summary(),
                diagnosisReport.rootCause(),
                evidenceJson,
                diagnosisReport.recommendation(),
                diagnosisReport.confidence()
        );
        return diagnosisRecordRepository.save(diagnosisRecord);
    }

    /** 统一序列化请求和证据，并将底层 JSON 异常转为业务可理解的异常。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }
}
