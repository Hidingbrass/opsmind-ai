package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;
import java.util.List;

/**
 * 从数据库查询诊断历史时返回的报告视图。
 *
 * @param id 诊断记录 id
 * @param incidentId 关联故障 id
 * @param traceId 报告对应的 OpenTelemetry 调用链 id
 * @param summary 诊断摘要
 * @param rootCause 根因
 * @param evidence 已从数据库 JSON 字符串还原的证据列表
 * @param recommendation 修复建议
 * @param confidence 置信度
 * @param agentMetadata 报告对应的执行模式和模型调用元数据
 * @param createdAt 报告保存时间
 */
public record DiagnosisRecordResponse(
        String id,
        String incidentId,
        String traceId,
        String summary,
        String rootCause,
        List<String> evidence,
        String recommendation,
        double confidence,
        AgentExecutionMetadata agentMetadata,
        Instant createdAt
) {
}
