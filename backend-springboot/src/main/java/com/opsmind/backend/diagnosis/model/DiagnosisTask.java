package com.opsmind.backend.diagnosis.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 记录一次异步 AI 诊断的执行进度。
 *
 * <p>本实体只保存任务状态和最终报告关联；诊断正文保存在 DiagnosisRecord，
 * 实时通知使用不入库的 DiagnosisTaskEvent。
 */
@Entity
@Table(name = "diagnosis_tasks")
public class DiagnosisTask {

    /** 诊断任务 id，也是前端订阅 SSE 的关联键。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 本任务要诊断的故障事件 id。 */
    @Column(nullable = false, length = 36)
    private String incidentId;

    /** 串联入口请求、异步执行、工具调用、AI 调用和最终报告的 OpenTelemetry traceId。 */
    @Column(length = 32)
    private String traceId;

    /** 当前任务状态，数据库是该状态的可靠来源。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosisTaskStatus status;

    /** 诊断成功后指向 DiagnosisRecord，未成功时为 null。 */
    @Column(length = 36)
    private String diagnosisRecordId;

    /** 诊断失败时保存的原因，成功时为 null。 */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    /** 任务创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** 任务最后一次状态更新时间。 */
    @Column(nullable = false)
    private Instant updatedAt;

    /** 异步执行器将任务改为 RUNNING 的时间。 */
    private Instant startedAt;

    /** 任务进入 SUCCESS 或 FAILED 的时间。 */
    private Instant finishedAt;

    /** JPA 反射创建实体时使用，业务代码不直接调用。 */
    protected DiagnosisTask() {
    }

    /**
     * @param incidentId 要诊断的故障事件 id
     * @param traceId 创建任务的入口请求 traceId
     */
    public DiagnosisTask(String incidentId, String traceId) {
        this.incidentId = incidentId;
        this.traceId = traceId;
    }

    /** 新任务落库时默认进入 PENDING，后续由后台执行器推进。 */
    @PrePersist
    void prePersist() {
        this.id = UUID.randomUUID().toString();
        this.status = DiagnosisTaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 任务更新入库前刷新 updatedAt，方便排查任务停留时间。 */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** @return 诊断任务 id */
    public String getId() {
        return id;
    }

    /** @return 关联故障 id */
    public String getIncidentId() {
        return incidentId;
    }

    /** @return 贯穿本次诊断全链路的 OpenTelemetry traceId */
    public String getTraceId() {
        return traceId;
    }

    /** @return 任务当前状态 */
    public DiagnosisTaskStatus getStatus() {
        return status;
    }

    /** @return 成功报告 id，任务未成功时为 null */
    public String getDiagnosisRecordId() {
        return diagnosisRecordId;
    }

    /** @return 失败原因，任务未失败时为 null */
    public String getFailureReason() {
        return failureReason;
    }

    /** @return 任务创建时间 */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return 任务最后更新时间 */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return 后台执行开始时间 */
    public Instant getStartedAt() {
        return startedAt;
    }

    /** @return 任务终止时间 */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** 由异步执行器调用，将 PENDING 推进为 RUNNING 并记录开始时间。 */
    public void markRunning() {
        this.status = DiagnosisTaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /** @param diagnosisRecordId 已保存的最终报告 id */
    public void markSuccess(String diagnosisRecordId) {
        this.status = DiagnosisTaskStatus.SUCCESS;
        this.diagnosisRecordId = diagnosisRecordId;
        this.finishedAt = Instant.now();
    }

    /** @param failureReason 调用 AI、序列化或保存过程中的失败原因 */
    public void markFailed(String failureReason) {
        this.status = DiagnosisTaskStatus.FAILED;
        this.failureReason = failureReason;
        this.finishedAt = Instant.now();
    }
}
