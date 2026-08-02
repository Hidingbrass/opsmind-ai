package com.opsmind.backend.diagnosis.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 已完成并可历史查询的诊断报告。
 *
 * <p>与 DiagnosisTask 的区别：Task 记录执行进度，Record 保存 AI 最终产出的业务内容。
 */
@Entity
@Table(name = "diagnosis_records")
public class DiagnosisRecord {

    /** 诊断报告唯一 id。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 报告所属故障事件 id。 */
    @Column(nullable = false, length = 36)
    private String incidentId;

    /** 生成本报告时对应的完整诊断链路 id。 */
    @Column(length = 32)
    private String traceId;

    /** 面向用户的简短诊断摘要。 */
    @Column(nullable = false, length = 1000)
    private String summary;

    /** AI 基于证据判断的根因。 */
    @Column(nullable = false, length = 1000)
    private String rootCause;

    /** 证据列表序列化后的 JSON，TEXT 用于避免固定长度限制。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    /** 排查、修复或降级建议。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    /** 0 到 1 之间的诊断置信度。 */
    @Column(nullable = false)
    private double confidence;

    /** 报告入库时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 反射专用无参构造函数。 */
    protected DiagnosisRecord() {
    }

    /** 根据 AI 报告内容创建待持久化的诊断记录。 */
    public DiagnosisRecord(
            String incidentId,
            String traceId,
            String summary,
            String rootCause,
            String evidenceJson,
            String recommendation,
            double confidence
    ) {
        this.incidentId = incidentId;
        this.traceId = traceId;
        this.summary = summary;
        this.rootCause = rootCause;
        this.evidenceJson = evidenceJson;
        this.recommendation = recommendation;
        this.confidence = confidence;
    }

    /** 首次入库前生成报告 id 和创建时间。 */
    @PrePersist
    void prePersist() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    /** @return 诊断报告 id */
    public String getId() {
        return id;
    }

    /** @return 关联故障 id */
    public String getIncidentId() {
        return incidentId;
    }

    /** @return 串联任务、工具和 AI 调用的 OpenTelemetry traceId */
    public String getTraceId() {
        return traceId;
    }

    /** @return 诊断摘要 */
    public String getSummary() {
        return summary;
    }

    /** @return 根因 */
    public String getRootCause() {
        return rootCause;
    }

    /** @return 证据列表 JSON 字符串 */
    public String getEvidenceJson() {
        return evidenceJson;
    }

    /** @return 修复建议 */
    public String getRecommendation() {
        return recommendation;
    }

    /** @return 诊断置信度 */
    public double getConfidence() {
        return confidence;
    }

    /** @return 报告入库时间 */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
