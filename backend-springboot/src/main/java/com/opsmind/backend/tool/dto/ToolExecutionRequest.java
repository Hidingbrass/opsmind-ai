package com.opsmind.backend.tool.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Python AI Agent 请求 Spring Tool Gateway 执行工具时的通用合同。
 *
 * @param taskId 工具调用所属异步诊断任务，用于审计和 SSE 关联
 * @param incidentId 工具调用所属故障，Gateway 会校验其与 taskId 一致
 * @param toolName AI 选择的工具名，只能命中 Gateway 白名单
 * @param arguments 来自 Function Calling JSON 的动态参数对象
 */
public record ToolExecutionRequest(
        @NotBlank(message = "taskId 不能为空")
        @Size(max = 36, message = "taskId 不能超过 36 个字符")
        String taskId,
        @NotBlank(message = "incidentId 不能为空")
        @Size(max = 36, message = "incidentId 不能超过 36 个字符")
        String incidentId,
        @NotBlank(message = "toolName 不能为空")
        @Size(max = 50, message = "toolName 不能超过 50 个字符")
        String toolName,
        @NotNull(message = "arguments 不能为空")
        @Size(max = 20, message = "arguments 参数数量不能超过 20 个")
        Map<String, Object> arguments
) {
}
