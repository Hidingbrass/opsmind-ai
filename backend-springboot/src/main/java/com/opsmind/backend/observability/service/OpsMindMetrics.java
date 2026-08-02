package com.opsmind.backend.observability.service;

import java.time.Duration;
import java.util.Set;

import com.opsmind.backend.diagnosis.dto.AgentExecutionMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * OpsMind 平台自身的业务指标入口。
 *
 * <p>所有标签都限制在固定集合中，避免把 taskId、incidentId 等高基数字段写入
 * Prometheus，导致时间序列数量失控。
 */
@Component
public class OpsMindMetrics {

    /** 只允许固定工具名成为标签，未知输入统一归为 unknown。 */
    private static final Set<String> KNOWN_TOOLS = Set.of(
            "queryLogs",
            "queryMetrics",
            "queryTrace",
            "searchRunbook",
            "getRecentDeployments",
            "generateIncidentReport"
    );

    /** 执行模式也是固定低基数标签，模型名和 Prompt 版本只进入审计表。 */
    private static final Set<String> KNOWN_EXECUTION_MODES = Set.of(
            "DETERMINISTIC",
            "LLM",
            "LLM_FALLBACK"
    );

    /** Micrometer 指标注册中心，Prometheus 会从这里抓取所有 Meter。 */
    private final MeterRegistry meterRegistry;

    /** 由 Spring 注入当前应用的指标注册中心。 */
    public OpsMindMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 记录一条新建任务。 */
    public void taskCreated() {
        meterRegistry.counter("opsmind.diagnosis.tasks", "status", "created").increment();
    }

    /** 记录任务终态和从开始执行到终态的耗时。 */
    public void taskFinished(boolean successful, Duration duration) {
        String status = successful ? "success" : "failed";
        meterRegistry.counter("opsmind.diagnosis.tasks", "status", status).increment();
        meterRegistry.timer("opsmind.diagnosis.task.duration", "status", status)
                .record(duration);
    }

    /** 记录工具名、结果状态和毫秒耗时。 */
    public void toolCall(String toolName, boolean successful, long latencyMs) {
        String normalizedTool = KNOWN_TOOLS.contains(toolName) ? toolName : "unknown";
        String status = successful ? "success" : "failed";
        meterRegistry.counter(
                "opsmind.tool.calls",
                "tool", normalizedTool,
                "status", status
        ).increment();
        meterRegistry.timer(
                "opsmind.tool.call.duration",
                "tool", normalizedTool,
                "status", status
        ).record(Duration.ofMillis(Math.max(latencyMs, 0)));
    }

    /** 记录 Spring 调用 Python AI 服务的结果和耗时。 */
    public void aiCall(boolean successful, Duration duration) {
        String status = successful ? "success" : "failed";
        meterRegistry.counter("opsmind.ai.calls", "status", status).increment();
        meterRegistry.timer("opsmind.ai.call.duration", "status", status).record(duration);
    }

    /** 记录模型 Token 和 Agent 工具次数，不把可变模型名写入 Prometheus 标签。 */
    public void agentExecution(AgentExecutionMetadata metadata) {
        if (metadata == null) {
            return;
        }
        String mode = KNOWN_EXECUTION_MODES.contains(metadata.executionMode())
                ? metadata.executionMode().toLowerCase()
                : "unknown";
        meterRegistry.counter("opsmind.agent.executions", "mode", mode).increment();
        meterRegistry.counter(
                "opsmind.agent.tokens",
                "mode", mode,
                "direction", "input"
        ).increment(Math.max(metadata.inputTokens(), 0));
        meterRegistry.counter(
                "opsmind.agent.tokens",
                "mode", mode,
                "direction", "output"
        ).increment(Math.max(metadata.outputTokens(), 0));
        meterRegistry.counter("opsmind.agent.tool.calls", "mode", mode)
                .increment(Math.max(metadata.toolCallCount(), 0));
    }
}
