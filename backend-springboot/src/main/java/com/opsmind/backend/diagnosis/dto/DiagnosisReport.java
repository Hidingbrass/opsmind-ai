package com.opsmind.backend.diagnosis.dto;

import java.util.List;

/**
 * Python AI 服务生成的结构化诊断结果，也是 Java 与 Python 之间的核心响应合同。
 *
 * @param incidentId 被诊断的故障事件 id
 * @param traceId 串联本报告对应平台调用链的 OpenTelemetry traceId
 * @param summary 面向用户的简短诊断摘要
 * @param rootCause 根因判断
 * @param evidence 支撑结论的日志、指标、Trace 和 Runbook 证据
 * @param recommendation 建议排查或修复动作
 * @param confidence 0 到 1 之间的置信度
 */
public record DiagnosisReport(
        String incidentId,
        String traceId,
        String summary,
        String rootCause,
        List<String> evidence,
        String recommendation,
        double confidence
) {
}
