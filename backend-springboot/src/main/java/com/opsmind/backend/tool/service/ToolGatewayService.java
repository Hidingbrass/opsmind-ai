package com.opsmind.backend.tool.service;

import java.util.Map;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskService;
import com.opsmind.backend.observability.service.ObservabilityService;
import com.opsmind.backend.tool.dto.ToolExecutionRequest;
import com.opsmind.backend.tool.dto.ToolExecutionResponse;
import org.springframework.stereotype.Service;

/**
 * AI 工具执行的安全边界：校验诊断上下文，只分发白名单工具，并将异常转换为结构化失败。
 */
@Service
public class ToolGatewayService {
    /** 查询任务并校验 taskId 与 incidentId 归属关系。 */
    private final DiagnosisTaskService diagnosisTaskService;
    /** 实际提供 queryLogs 等只读观测能力。 */
    private final ObservabilityService observabilityService;
    /** 在工具响应确定后，尽力保存成功或失败审计。 */
    private final ToolCallAuditService toolCallAuditService;

    /** 由 Spring 注入 Tool Gateway 编排需要的业务服务。 */
    public ToolGatewayService(
            DiagnosisTaskService diagnosisTaskService,
            ObservabilityService observabilityService,
            ToolCallAuditService toolCallAuditService
    ) {
        this.diagnosisTaskService = diagnosisTaskService;
        this.observabilityService = observabilityService;
        this.toolCallAuditService = toolCallAuditService;
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
        ToolExecutionResponse response;
        try {
            validateRequest(request);
            DiagnosisTaskResponse task = diagnosisTaskService.getTask(request.taskId());

            if (!task.incidentId().equals(request.incidentId())) {
                throw new IllegalArgumentException("taskId 与 incidentId 不属于同一次诊断");
            }

            Object data = executeTool(request.toolName(), request.arguments());

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
        toolCallAuditService.record(request, response);
        return response;
    }

    /** 根据精确工具名在白名单中分发，未知工具统一拒绝。 */
    private Object executeTool(String toolName, Map<String, Object> arguments) {
        // 只分发明确列入白名单的工具，不根据 AI 输入反射调用任意 Java 方法。
        return switch (toolName) {
            case "queryLogs" -> executeQueryLogs(arguments);
            default -> throw new IllegalArgumentException("不支持的工具: " + toolName);
        };
    }

    /** 校验 queryLogs 专属 serviceName 参数并委托 ObservabilityService 查询。 */
    private Object executeQueryLogs(Map<String, Object> arguments) {
        Object serviceNameValue = arguments.get("serviceName");

        if (!(serviceNameValue instanceof String serviceName) || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName 必须是非空字符串");
        }

        return observabilityService.queryLogs(serviceName);
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
}
