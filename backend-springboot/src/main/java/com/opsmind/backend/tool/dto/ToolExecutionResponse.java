package com.opsmind.backend.tool.dto;

/**
 * Tool Gateway 返回给 AI Agent 的结构化执行结果。
 *
 * @param toolName 实际尝试执行的工具名
 * @param status 工具级 SUCCESS/FAILED，与外层 Result.code 的 HTTP 处理状态不同
 * @param data 成功时的结构化数据，失败时为 null
 * @param errorMessage 失败原因，成功时为 null
 * @param latencyMs 从 Gateway 接收请求到获得结果的毫秒耗时
 */
public record ToolExecutionResponse(
        String toolName,
        ToolExecutionStatus status,
        Object data,
        String errorMessage,
        long latencyMs
) {
    /** @return 自动补齐 SUCCESS、data 和空 errorMessage 的响应 */
    public static ToolExecutionResponse success(String toolName, Object data, long latencyMs) {
        return new ToolExecutionResponse(
                toolName,
                ToolExecutionStatus.SUCCESS,
                data,
                null,
                latencyMs
        );
    }

    /** @return 自动补齐 FAILED、空 data 和失败原因的响应 */
    public static ToolExecutionResponse failed(String toolName, String errorMessage, long latencyMs) {
        return new ToolExecutionResponse(
                toolName,
                ToolExecutionStatus.FAILED,
                null,
                errorMessage,
                latencyMs
        );
    }
}
