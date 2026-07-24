package com.opsmind.backend.diagnosis.dto;

import java.time.Instant;
import java.util.List;

/**
 * 根据已完成诊断生成的事故复盘视图。
 *
 * @param incidentId 故障 id
 * @param title 复盘标题
 * @param impact 故障影响摘要
 * @param rootCause 已确认根因
 * @param timeline 从故障创建到诊断完成的关键节点
 * @param correctiveActions 后续修复和预防动作
 * @param generatedAt 复盘生成时间
 */
public record IncidentReportResponse(
        String incidentId,
        String title,
        String impact,
        String rootCause,
        List<String> timeline,
        List<String> correctiveActions,
        Instant generatedAt
) {
}
