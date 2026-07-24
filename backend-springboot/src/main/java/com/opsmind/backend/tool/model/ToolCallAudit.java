package com.opsmind.backend.tool.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 工具调用的不可变审计记录。
 *
 * <p>它记录“谁在哪次诊断中调用了什么”，只保存响应摘要而不重复保存大量日志正文。
 */
@Entity
@Table(name = "tool_call_audits")
public class ToolCallAudit {

    /** 审计记录唯一 id，由 @PrePersist 回调在首次入库前生成。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 关联诊断任务；非法请求缺少 taskId 时允许为 null。 */
    @Column(length = 36)
    private String taskId;

    /** 关联故障事件；非法请求可能为 null。 */
    @Column(length = 36)
    private String incidentId;

    /** 关联入口请求与异步任务的 OpenTelemetry traceId。 */
    @Column(length = 32)
    private String traceId;

    /** 请求执行的工具名，未知工具也要留痕。 */
    @Column(nullable = false, length = 100)
    private String toolName;

    /** 工具请求的 JSON，后续写入前需对敏感参数脱敏。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestPayload;

    /** 成功结果的短摘要，例如“返回 2 条日志”。 */
    @Column(columnDefinition = "TEXT")
    private String responseSummary;

    /** 审计维度的执行状态，与 HTTP 外层状态分离。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ToolCallStatus status;

    /** 从 Tool Gateway 接收请求到工具返回的毫秒耗时。 */
    @Column(nullable = false)
    private long latencyMs;

    /** 工具失败原因，成功时为 null。 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 审计记录入库时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 反射专用无参构造函数。 */
    protected ToolCallAudit() {
    }

    /**
     * 根据一次已完成的工具执行结果创建待入库审计记录。
     *
     * <p>id 和 createdAt 不由调用者传入，而是交给 JPA 生命周期回调统一生成。
     */
    public ToolCallAudit(
            String taskId,
            String incidentId,
            String traceId,
            String toolName,
            String requestPayload,
            String responseSummary,
            ToolCallStatus status,
            long latencyMs,
            String errorMessage
    ) {
        this.taskId = taskId;
        this.incidentId = incidentId;
        this.traceId = traceId;
        this.toolName = toolName;
        this.requestPayload = requestPayload;
        this.responseSummary = responseSummary;
        this.status = status;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    /** 首次持久化前生成审计 id 和入库时间。 */
    @PrePersist
    void prePersist() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    /** 返回审计记录唯一 id。 */
    public String getId() {
        return id;
    }

    /** 返回本次工具调用关联的诊断任务 id。 */
    public String getTaskId() {
        return taskId;
    }

    /** 返回本次工具调用关联的故障事件 id。 */
    public String getIncidentId() {
        return incidentId;
    }

    /** 返回可在 Tempo 和平台审计中检索的 traceId。 */
    public String getTraceId() {
        return traceId;
    }

    /** 返回实际请求执行的工具名。 */
    public String getToolName() {
        return toolName;
    }

    /** 返回内部序列化请求；对外 DTO 不暴露该字段。 */
    public String getRequestPayload() {
        return requestPayload;
    }

    /** 返回工具成功响应的短摘要。 */
    public String getResponseSummary() {
        return responseSummary;
    }

    /** 返回本次工具执行的审计状态。 */
    public ToolCallStatus getStatus() {
        return status;
    }

    /** 返回本次工具执行的总耗时，单位为毫秒。 */
    public long getLatencyMs() {
        return latencyMs;
    }

    /** 返回工具执行失败原因；成功时为 null。 */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 返回审计记录首次入库的时间。 */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
