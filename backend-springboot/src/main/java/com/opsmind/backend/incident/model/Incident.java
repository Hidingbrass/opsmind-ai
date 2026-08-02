package com.opsmind.backend.incident.model;

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
 * 系统中一次可被诊断的故障事件，是观测数据、诊断任务和最终报告的业务起点。
 */
@Entity
@Table(name = "incidents")
public class Incident {

    /** 故障事件唯一标识，使用 UUID 便于跨服务传递。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 供用户和 AI 快速识别故障的简短标题。 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 故障主要所属的微服务名，用于筛选日志和指标。 */
    @Column(nullable = false, length = 80)
    private String serviceName;

    /** 故障严重程度，以字符串存库，避免枚举顺序变化破坏历史数据。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    /** 故障从打开、诊断到解决的业务状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    /** 用户或模拟场景观察到的外在现象，也是 RAG 检索的关键输入。 */
    @Column(nullable = false, length = 1000)
    private String symptom;

    /** 事件首次入库时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** 事件最后一次更新时间。 */
    @Column(nullable = false)
    private Instant updatedAt;

    /** 首次持久化前自动生成 id、初始 OPEN 状态和时间戳。 */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.status = IncidentStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 实体更新前自动刷新最后修改时间。 */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** @return 故障事件 id */
    public String getId() {
        return id;
    }

    /** @return 故障标题 */
    public String getTitle() {
        return title;
    }

    /** @return 故障所属服务名 */
    public String getServiceName() {
        return serviceName;
    }

    /** @return 故障严重程度 */
    public IncidentSeverity getSeverity() {
        return severity;
    }

    /** @return 故障当前状态 */
    public IncidentStatus getStatus() {
        return status;
    }

    /** @return 用户观察到的故障现象 */
    public String getSymptom() {
        return symptom;
    }

    /** @return 事件创建时间 */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return 事件最后更新时间 */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @param title 新的故障标题 */
    public void setTitle(String title) {
        this.title = title;
    }

    /** @param serviceName 新的故障所属服务名 */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /** @param severity 新的故障严重程度 */
    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    /** @param symptom 新的故障现象描述 */
    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }
}
