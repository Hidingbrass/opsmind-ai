package com.opsmind.backend.diagnosis.dto;

/**
 * 描述诊断报告的实际执行器，用于区分确定性流程、外部模型和模型失败后的降级。
 *
 * @param executionMode DETERMINISTIC、LLM 或 LLM_FALLBACK
 * @param provider 模型服务提供方或本地诊断器标识
 * @param modelName 实际配置的模型名
 * @param promptVersion 生成报告时使用的 Prompt 合同版本
 * @param inputTokens 外部模型输入 Token；确定性模式为 0
 * @param outputTokens 外部模型输出 Token；确定性模式为 0
 * @param toolCallCount 本次模型循环执行的工具次数；确定性模式为 0
 */
public record AgentExecutionMetadata(
        String executionMode,
        String provider,
        String modelName,
        String promptVersion,
        long inputTokens,
        long outputTokens,
        int toolCallCount
) {
    /** @return 兼容旧报告或缺失元数据时使用的诚实默认值 */
    public static AgentExecutionMetadata deterministic() {
        return new AgentExecutionMetadata(
                "DETERMINISTIC",
                "opsmind",
                "deterministic-rag-agent",
                "deterministic-v1",
                0,
                0,
                0
        );
    }
}
