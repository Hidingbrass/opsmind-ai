package com.opsmind.backend.diagnosis.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.backend.diagnosis.dto.DiagnosisRecordResponse;
import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.diagnosis.dto.IncidentReportResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisRecord;
import com.opsmind.backend.diagnosis.repository.DiagnosisRecordRepository;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import com.opsmind.backend.observability.service.ObservabilityService;
import com.opsmind.backend.observability.service.TraceContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    /** 带超时、重试、熔断和并发隔离的 AI 服务客户端。 */
    private final AiDiagnosisClient aiDiagnosisClient;
    /** Java 对象与 JSON 之间的序列化工具。 */
    private final ObjectMapper objectMapper;
    /** 诊断报告持久化仓库。 */
    private final DiagnosisRecordRepository diagnosisRecordRepository;
    /** 为同步诊断或内部调用补充当前 OpenTelemetry traceId。 */
    private final TraceContextService traceContextService;

    /** 由 Spring 构造器注入诊断链路需要的所有协作对象。 */
    public DiagnosisService(
            IncidentService incidentService,
            ObservabilityService observabilityService,
            AiDiagnosisClient aiDiagnosisClient,
            ObjectMapper objectMapper,
            DiagnosisRecordRepository diagnosisRecordRepository,
            TraceContextService traceContextService
    ) {
        this.incidentService = incidentService;
        this.observabilityService = observabilityService;
        this.aiDiagnosisClient = aiDiagnosisClient;
        this.objectMapper = objectMapper;
        this.diagnosisRecordRepository = diagnosisRecordRepository;
        this.traceContextService = traceContextService;
    }

    /**
     * 供同步 HTTP 接口使用，返回不含数据库内部字段的诊断报告。
     *
     * @param incidentId 故障 id
     * @return 已完成并保存的诊断结果
     */
    public DiagnosisReport diagnose(String incidentId) {
        // 同步调试接口没有异步任务上下文，因此显式传入 null。
        DiagnosisRecord savedRecord = diagnoseAndSaveRecord(
                null,
                incidentId,
                traceContextService.currentTraceId()
        );
        return toReport(savedRecord);
    }

    /**
     * 供异步执行器使用，返回已保存实体，便于任务记录 diagnosisRecordId。
     *
     * @param taskId 异步诊断任务 id；同步调试链路中为 null
     * @param incidentId 故障 id
     * @return 已生成数据库 id 的诊断记录
     */
    public DiagnosisRecord diagnoseAndSaveRecord(String taskId, String incidentId) {
        return diagnoseAndSaveRecord(taskId, incidentId, traceContextService.currentTraceId());
    }

    /**
     * 使用任务创建时保存的 traceId 执行并持久化诊断，保证异步线程仍属于原始业务链路。
     */
    public DiagnosisRecord diagnoseAndSaveRecord(
            String taskId,
            String incidentId,
            String traceId
    ) {
        DiagnosisReport report = requestAiDiagnosis(taskId, incidentId, traceId);
        DiagnosisReportValidator.validate(report, incidentId);
        return saveDiagnosisRecord(report);
    }

    /** 组装任务与故障上下文并调用 {@code POST /ai/diagnose}。 */
    private DiagnosisReport requestAiDiagnosis(
            String taskId,
            String incidentId,
            String traceId
    ) {
        Incident incident = incidentService.getById(incidentId);
        IncidentResponse incidentResponse = IncidentResponse.from(incident);
        String serviceName = incident.getServiceName();
        List<LogEntry> logs = observabilityService.queryLogs(serviceName);
        List<MetricPoint> metrics = observabilityService.queryMetrics(serviceName);
        // 先从日志提取 traceId，再查询链路，避免向 AI 发送与当前故障无关的 Span。
        List<TraceSpan> traces = logs.stream()
                .map(LogEntry::traceId)
                .distinct()
                .flatMap(observabilityTraceId ->
                        observabilityService.queryTrace(observabilityTraceId).stream()
                )
                .toList();

        // 把故障事件、日志、指标、链路追踪统一打包给 AI 服务，AI 才能基于完整上下文做诊断。
        DiagnosisRequest diagnosisRequest = new DiagnosisRequest(
                taskId,
                traceId,
                incidentResponse,
                logs,
                metrics,
                traces
        );
        String requestBody = toJson(diagnosisRequest);
        log.info(
                "调用 AI 诊断服务，taskId={}, incidentId={}, requestBodyLength={}",
                taskId,
                incidentId,
                requestBody.length()
        );

        return aiDiagnosisClient.diagnose(diagnosisRequest);
    }

    /** @return 按时间倒序排列的某故障诊断历史 */
    public List<DiagnosisRecordResponse> listRecords(String incidentId) {
        return diagnosisRecordRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 基于已保存诊断生成可展示的事故复盘，不再次调用 AI，也不修改故障状态。
     *
     * @param incidentId 已完成诊断的故障 id
     * @return 影响、根因、时间线和改进动作
     */
    public IncidentReportResponse generateIncidentReport(String incidentId) {
        Incident incident = incidentService.getById(incidentId);
        DiagnosisRecord latest = diagnosisRecordRepository
                .findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "该故障尚无诊断记录，无法生成复盘"
                ));

        return new IncidentReportResponse(
                incidentId,
                incident.getTitle() + " - 事故复盘",
                incident.getSeverity() + " 级故障：" + incident.getSymptom(),
                latest.getRootCause(),
                List.of(
                        "故障创建：" + incident.getCreatedAt(),
                        "诊断报告生成：" + latest.getCreatedAt()
                ),
                List.of(
                        latest.getRecommendation(),
                        "修复后重放对应故障场景并确认指标恢复",
                        "为本次异常信号补充告警阈值和 Runbook 演练"
                ),
                java.time.Instant.now()
        );
    }

    /** 将数据库记录转换为历史查询 DTO。 */
    private DiagnosisRecordResponse toResponse(DiagnosisRecord record) {
        return new DiagnosisRecordResponse(
                record.getId(),
                record.getIncidentId(),
                record.getTraceId(),
                record.getSummary(),
                record.getRootCause(),
                fromJsonToStringList(record.getEvidenceJson()),
                record.getRecommendation(),
                record.getConfidence(),
                record.getAgentMetadata(),
                record.getCreatedAt()
        );
    }

    /** 将数据库记录还原为同步诊断接口的报告格式。 */
    private DiagnosisReport toReport(DiagnosisRecord record) {
        return new DiagnosisReport(
                record.getIncidentId(),
                record.getTraceId(),
                record.getSummary(),
                record.getRootCause(),
                fromJsonToStringList(record.getEvidenceJson()),
                record.getRecommendation(),
                record.getConfidence(),
                record.getAgentMetadata()
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
                diagnosisReport.traceId(),
                diagnosisReport.summary(),
                diagnosisReport.rootCause(),
                evidenceJson,
                diagnosisReport.recommendation(),
                diagnosisReport.confidence(),
                diagnosisReport.agentMetadata()
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
