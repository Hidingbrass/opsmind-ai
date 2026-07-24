package com.opsmind.backend.diagnosis.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 记录 Spring 调用 Python AI 服务的单次尝试，用于延迟、失败和重试审计。 */
@Entity
@Table(name = "ai_call_audits")
public class AiCallAudit {

    /** 审计记录唯一 id。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 关联异步诊断任务；同步诊断时可以为空。 */
    @Column(length = 36)
    private String taskId;

    /** 关联待诊断故障。 */
    @Column(nullable = false, length = 36)
    private String incidentId;

    /** 关联完整诊断链路的 OpenTelemetry traceId。 */
    @Column(length = 32)
    private String traceId;

    /** 被调用的 AI 服务提供方标识。 */
    @Column(nullable = false, length = 60)
    private String provider;

    /** 诊断器或未来外部模型名称。 */
    @Column(nullable = false, length = 100)
    private String modelName;

    /** 本次调用成功或失败。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiCallStatus status;

    /** 从发起 HTTP 调用到响应或异常的总耗时。 */
    @Column(nullable = false)
    private long latencyMs;

    /** 失败时的安全短消息，成功时为空。 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 审计记录首次入库时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 反射使用的无参构造函数。 */
    protected AiCallAudit() {
    }

    /** 根据一次 Spring 到 Python 的调用结果创建待入库审计记录。 */
    public AiCallAudit(
            String taskId,
            String incidentId,
            String traceId,
            String provider,
            String modelName,
            AiCallStatus status,
            long latencyMs,
            String errorMessage
    ) {
        this.taskId = taskId;
        this.incidentId = incidentId;
        this.traceId = traceId;
        this.provider = provider;
        this.modelName = modelName;
        this.status = status;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    /** 首次入库前生成 id 和创建时间。 */
    @PrePersist
    void prePersist() {
        id = UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    /** @return 审计记录 id */
    public String getId() {
        return id;
    }

    /** @return 关联任务 id；同步诊断时为 null */
    public String getTaskId() {
        return taskId;
    }

    /** @return 关联故障 id */
    public String getIncidentId() {
        return incidentId;
    }

    /** @return 贯穿诊断链路的 OpenTelemetry traceId */
    public String getTraceId() {
        return traceId;
    }

    /** @return 被调用的 AI 服务提供方标识 */
    public String getProvider() {
        return provider;
    }

    /** @return 当前诊断工作流或未来外部模型名称 */
    public String getModelName() {
        return modelName;
    }

    /** @return 本次调用成功或失败 */
    public AiCallStatus getStatus() {
        return status;
    }

    /** @return 调用耗时，单位为毫秒 */
    public long getLatencyMs() {
        return latencyMs;
    }

    /** @return 安全失败短消息；成功时为 null */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** @return 审计记录入库时间 */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
