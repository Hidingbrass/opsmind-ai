package com.opsmind.backend.diagnosis.service;

import java.util.List;

import com.opsmind.backend.diagnosis.dto.DiagnosisReport;

/** 校验 Python 返回内容满足 Java 持久化和展示边界。 */
final class DiagnosisReportValidator {

    private static final int MAX_REPORT_TEXT_LENGTH = 1000;
    private static final int MAX_RECOMMENDATION_LENGTH = 4000;
    private static final int MAX_EVIDENCE_ITEMS = 20;
    private static final int MAX_EVIDENCE_ITEM_LENGTH = 500;

    private DiagnosisReportValidator() {
    }

    /** 外部 AI 响应即使已通过 Python 校验，Java 仍在入库前独立复核。 */
    static void validate(DiagnosisReport report, String expectedIncidentId) {
        if (report == null) {
            throw new IllegalArgumentException("AI 诊断报告不能为空");
        }
        if (!expectedIncidentId.equals(report.incidentId())) {
            throw new IllegalArgumentException("AI 诊断报告的 incidentId 与当前任务不一致");
        }
        requireBounded(report.summary(), "summary", MAX_REPORT_TEXT_LENGTH);
        requireBounded(report.rootCause(), "rootCause", MAX_REPORT_TEXT_LENGTH);
        requireBounded(
                report.recommendation(),
                "recommendation",
                MAX_RECOMMENDATION_LENGTH
        );
        List<String> evidence = report.evidence();
        if (evidence == null || evidence.isEmpty() || evidence.size() > MAX_EVIDENCE_ITEMS) {
            throw new IllegalArgumentException("AI 诊断报告的 evidence 数量不合法");
        }
        for (String item : evidence) {
            requireBounded(item, "evidence", MAX_EVIDENCE_ITEM_LENGTH);
        }
        if (!Double.isFinite(report.confidence())
                || report.confidence() < 0
                || report.confidence() > 1) {
            throw new IllegalArgumentException("AI 诊断报告的 confidence 不合法");
        }
    }

    private static void requireBounded(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("AI 诊断报告的 " + field + " 长度不合法");
        }
    }
}
