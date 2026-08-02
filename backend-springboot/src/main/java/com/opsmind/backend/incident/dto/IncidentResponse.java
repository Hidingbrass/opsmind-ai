package com.opsmind.backend.incident.dto;

import java.time.Instant;

import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.model.IncidentSeverity;
import com.opsmind.backend.incident.model.IncidentStatus;

/**
 * 对外返回的故障事件快照，避免 Controller 直接暴露 JPA 实体。
 *
 * @param id 故障事件 id
 * @param title 故障标题
 * @param serviceName 所属服务
 * @param severity 严重程度
 * @param status 当前业务状态
 * @param symptom 故障现象
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record IncidentResponse(
        String id,
        String title,
        String serviceName,
        IncidentSeverity severity,
        IncidentStatus status,
        String symptom,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 将内部 Incident 实体复制成稳定的 API 响应对象。
     *
     * @param incident 数据库中的故障实体
     * @return 可被 JSON 序列化的响应快照
     */
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getSymptom(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
