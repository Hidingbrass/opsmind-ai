package com.opsmind.backend.fault.model;

import com.opsmind.backend.incident.model.IncidentSeverity;

/**
 * 可重复演示和验收的预置故障模板，本身不入库，注入时会转换为真实 Incident。
 *
 * @param key curl 和页面使用的稳定场景标识
 * @param title 故障标题
 * @param serviceName 故障主要所属服务
 * @param severity 故障严重程度
 * @param symptom 注入后的可观察现象
 * @param expectedRootCause 用于演示和评测的期望根因
 * @param suggestedAction 用于对照诊断结果的建议动作
 */
public record FaultScenario(
        String key,
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom,
        String expectedRootCause,
        String suggestedAction
) {
}
