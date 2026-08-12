package com.opsmind.backend.tool.service;

import java.util.Map;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskEventPublisher;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskService;
import com.opsmind.backend.diagnosis.service.DiagnosisService;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.service.ObservabilityService;
import com.opsmind.backend.observability.service.OpsMindMetrics;
import com.opsmind.backend.tool.dto.ToolExecutionRequest;
import com.opsmind.backend.tool.dto.ToolExecutionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * AI 工具执行的安全边界：校验诊断上下文，只分发白名单工具，并将异常转换为结构化失败。
 */
@Service
public class ToolGatewayService {
    /** 查询任务并校验 taskId 与 incidentId 归属关系。 */
    private final DiagnosisTaskService diagnosisTaskService;
    /** 生成已完成故障的事故复盘报告。 */
    private final DiagnosisService diagnosisService;
    /** 查询 Incident 真实服务归属，防止模型跨服务读取观测数据。 */
    private final IncidentService incidentService;
    /** 实际提供 queryLogs 等只读观测能力。 */
    private final ObservabilityService observabilityService;
    /** 在工具响应确定后，尽力保存成功或失败审计。 */
    private final ToolCallAuditService toolCallAuditService;
    /** 把工具执行阶段推送给订阅诊断任务的前端。 */
    private final DiagnosisTaskEventPublisher diagnosisTaskEventPublisher;
    /** searchRunbook 工具通过该客户端访问 Python 的知识库检索接口。 */
    private final RestClient restClient;
    /** Python AI 服务地址，Docker 部署时可由环境变量覆盖。 */
    private final String aiBaseUrl;
    /** 记录各工具成功率和执行耗时。 */
    private final OpsMindMetrics opsMindMetrics;

    /** 由 Spring 注入 Tool Gateway 编排需要的业务服务。 */
    public ToolGatewayService(
            DiagnosisTaskService diagnosisTaskService,
            DiagnosisService diagnosisService,
            IncidentService incidentService,
            ObservabilityService observabilityService,
            ToolCallAuditService toolCallAuditService,
            DiagnosisTaskEventPublisher diagnosisTaskEventPublisher,
            RestClient restClient,
            @Value("${opsmind.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            OpsMindMetrics opsMindMetrics
    ) {
        this.diagnosisTaskService = diagnosisTaskService;
        this.diagnosisService = diagnosisService;
        this.incidentService = incidentService;
        this.observabilityService = observabilityService;
        this.toolCallAuditService = toolCallAuditService;
        this.diagnosisTaskEventPublisher = diagnosisTaskEventPublisher;
        this.restClient = restClient;
        this.aiBaseUrl = aiBaseUrl;
        this.opsMindMetrics = opsMindMetrics;
    }

    /**
     * 使用单调的 nanoTime 计算耗时，不受系统时钟校准影响。
     *
     * @param startedAt 工具调用开始时的纳秒计数
     * @return 已经过的整数毫秒
     */
    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 执行完整网关流程：通用校验 -> 任务归属校验 -> 白名单分发 -> 结果包装。
     *
     * @param request AI Agent 生成的工具调用请求
     * @return 永远结构化的成功或失败结果
     */
    public ToolExecutionResponse execute(ToolExecutionRequest request) {
        long startedAt = System.nanoTime();
        String toolName = request == null ? "unknown" : request.toolName();
        String traceId = null;
        ToolExecutionResponse response;
        try {
            validateRequest(request);
            DiagnosisTaskResponse task = diagnosisTaskService.getTask(request.taskId());
            traceId = task.traceId();

            if (!task.incidentId().equals(request.incidentId())) {
                throw new IllegalArgumentException("taskId 与 incidentId 不属于同一次诊断");
            }
            Incident incident = incidentService.getById(request.incidentId());

            diagnosisTaskEventPublisher.publish(
                    request.taskId(),
                    DiagnosisTaskEvent.running(
                            request.taskId(),
                            "TOOL_CALL",
                            "正在执行诊断工具: " + request.toolName()
                    )
            );
            Object data = executeTool(
                    request.toolName(),
                    request.arguments(),
                    incident
            );

            response = ToolExecutionResponse.success(
                    request.toolName(),
                    data,
                    elapsedMillis(startedAt)
            );
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();

            response = ToolExecutionResponse.failed(
                    toolName,
                    errorMessage,
                    elapsedMillis(startedAt)
            );
        }
        // response 已经确定，审计记录会与 Agent 收到的成功或失败状态保持一致。
        toolCallAuditService.record(request, response, traceId);
        opsMindMetrics.toolCall(
                response.toolName(),
                response.status() == com.opsmind.backend.tool.dto.ToolExecutionStatus.SUCCESS,
                response.latencyMs()
        );
        publishToolResult(request, response);
        return response;
    }

    /** 根据精确工具名在白名单中分发，未知工具统一拒绝。 */
    private Object executeTool(
            String toolName,
            Map<String, Object> arguments,
            Incident incident
    ) {
        // 只分发明确列入白名单的工具，不根据 AI 输入反射调用任意 Java 方法。
        return switch (toolName) {
            case "queryLogs" -> executeQueryLogs(arguments, incident);
            case "queryMetrics" -> executeQueryMetrics(arguments, incident);
            case "queryTrace" -> executeQueryTrace(arguments, incident);
            case "searchRunbook" -> executeSearchRunbook(arguments);
            case "getRecentDeployments" ->
                    executeGetRecentDeployments(arguments, incident);
            case "generateIncidentReport" ->
                    executeGenerateIncidentReport(arguments, incident.getId());
            default -> throw new IllegalArgumentException("不支持的工具: " + toolName);
        };
    }

    /** 校验 queryLogs 专属 serviceName 参数并委托 ObservabilityService 查询。 */
    private Object executeQueryLogs(Map<String, Object> arguments, Incident incident) {
        String serviceName = requireCurrentService(arguments, incident);
        return observabilityService.queryLogs(serviceName);
    }

    /** 校验服务名并查询该服务的延迟、错误率或连接类指标。 */
    private Object executeQueryMetrics(Map<String, Object> arguments, Incident incident) {
        String serviceName = requireCurrentService(arguments, incident);
        return observabilityService.queryMetrics(serviceName);
    }

    /** 校验 traceId 并查询同一条调用链中的全部节点。 */
    private Object executeQueryTrace(Map<String, Object> arguments, Incident incident) {
        String traceId = requireArgumentText(arguments, "traceId");
        boolean belongsToIncident = observabilityService
                .queryLogs(incident.getServiceName())
                .stream()
                .anyMatch(logEntry -> traceId.equals(logEntry.traceId()));
        if (!belongsToIncident) {
            throw new IllegalArgumentException("traceId 不属于当前故障服务");
        }
        return observabilityService.queryTrace(traceId);
    }

    /** 查询服务最近发布记录，供 Agent 判断故障是否与变更时间相关。 */
    private Object executeGetRecentDeployments(
            Map<String, Object> arguments,
            Incident incident
    ) {
        String serviceName = requireCurrentService(arguments, incident);
        return observabilityService.getRecentDeployments(serviceName);
    }

    /**
     * 生成复盘前再次校验 arguments 中的 incidentId 与任务归属一致，禁止跨故障读取。
     */
    private Object executeGenerateIncidentReport(
            Map<String, Object> arguments,
            String requestIncidentId
    ) {
        String incidentId = requireArgumentText(arguments, "incidentId");
        if (!incidentId.equals(requestIncidentId)) {
            throw new IllegalArgumentException("复盘 incidentId 与诊断任务不一致");
        }
        return diagnosisService.generateIncidentReport(incidentId);
    }

    /**
     * 通过 Python 的 RAG 接口检索 Runbook。
     *
     * <p>虽然检索实现位于 Python，入口仍经过 Tool Gateway，因此任务归属、审计和
     * SSE 工具阶段与其他工具保持一致。
     */
    @SuppressWarnings("unchecked")
    private Object executeSearchRunbook(Map<String, Object> arguments) {
        String query = requireArgumentText(arguments, "query");
        if (query.length() > 500) {
            throw new IllegalArgumentException("query 长度不能超过 500");
        }
        int nResults = requirePositiveInt(arguments.get("nResults"), "nResults", 3);

        Map<String, Object> response = restClient.get()
                .uri(
                        aiBaseUrl + "/ai/runbooks/search?query={query}&n_results={nResults}",
                        query,
                        nResults
                )
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("results") instanceof java.util.List<?> results)) {
            throw new IllegalStateException("Runbook 检索响应缺少 results");
        }
        return results;
    }

    /** 校验所有工具共用的请求字段，不处理具体工具的专属参数。 */
    private void validateRequest(ToolExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("工具执行请求不能为空");
        }
        requireText(request.taskId(), "taskId");
        requireText(request.incidentId(), "incidentId");
        requireText(request.toolName(), "toolName");

        if (request.arguments() == null) {
            throw new IllegalArgumentException("arguments 不能为空");
        }
    }

    /** 复用的必填字符串校验，同时拒绝 null、空字符串和纯空格。 */
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    /** 从动态 arguments 中提取必填字符串，拒绝类型错误、空串和纯空格。 */
    private String requireArgumentText(Map<String, Object> arguments, String fieldName) {
        Object value = arguments.get(fieldName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 必须是非空字符串");
        }
        return text;
    }

    /** 只允许模型查询当前 Incident 所属服务，拒绝任意 serviceName。 */
    private String requireCurrentService(
            Map<String, Object> arguments,
            Incident incident
    ) {
        String serviceName = requireArgumentText(arguments, "serviceName");
        if (!incident.getServiceName().equals(serviceName)) {
            throw new IllegalArgumentException("serviceName 不属于当前故障");
        }
        return serviceName;
    }

    /** 提取可选正整数参数；未传时使用默认值，并限制单次检索规模。 */
    private int requirePositiveInt(Object value, String fieldName, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(fieldName + " 必须是正整数");
        }
        int result = number.intValue();
        if (result <= 0 || result > 10) {
            throw new IllegalArgumentException(fieldName + " 必须在 1 到 10 之间");
        }
        return result;
    }

    /** 工具审计完成后推送结果阶段；数据库审计仍是可靠记录，SSE 只负责实时展示。 */
    private void publishToolResult(
            ToolExecutionRequest request,
            ToolExecutionResponse response
    ) {
        if (request == null || request.taskId() == null || request.taskId().isBlank()) {
            return;
        }
        String stage = response.status() == com.opsmind.backend.tool.dto.ToolExecutionStatus.SUCCESS
                ? "TOOL_SUCCESS"
                : "TOOL_FAILED";
        String message = response.status() == com.opsmind.backend.tool.dto.ToolExecutionStatus.SUCCESS
                ? "诊断工具执行成功: " + response.toolName()
                : "诊断工具执行失败: " + response.toolName();
        diagnosisTaskEventPublisher.publish(
                request.taskId(),
                DiagnosisTaskEvent.running(request.taskId(), stage, message)
        );
    }
}
