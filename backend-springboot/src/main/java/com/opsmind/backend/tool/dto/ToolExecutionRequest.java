package com.opsmind.backend.tool.dto;

import java.util.Map;

/**
 * Python AI Agent 请求 Spring Tool Gateway 执行工具时的通用合同。
 *
 * @param taskId 工具调用所属异步诊断任务，用于审计和 SSE 关联
 * @param incidentId 工具调用所属故障，Gateway 会校验其与 taskId 一致
 * @param toolName AI 选择的工具名，只能命中 Gateway 白名单
 * @param arguments 来自 Function Calling JSON 的动态参数对象
 */
public record ToolExecutionRequest(
        String taskId,
        String incidentId,
        String toolName,
        Map<String, Object> arguments
) {
}
