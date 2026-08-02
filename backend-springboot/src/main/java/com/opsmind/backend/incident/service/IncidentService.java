package com.opsmind.backend.incident.service;

import java.util.List;

import com.opsmind.backend.incident.dto.CreateIncidentRequest;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;

/**
 * 故障事件的核心业务服务，为场景注入、诊断和工具网关提供统一的事件查询入口。
 */
@Service
public class IncidentService {

    /** 故障事件的数据库访问对象。 */
    private final IncidentRepository incidentRepository;

    /** @param incidentRepository Spring Data 提供的故障事件仓库 */
    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /**
     * 根据请求创建 OPEN 状态的故障事件。
     *
     * @param request 故障标题、服务、级别和现象
     * @return 已持久化并生成 id 的实体
     */
    public Incident create(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setServiceName(request.serviceName());
        incident.setSeverity(request.severity());
        incident.setSymptom(request.symptom());
        return incidentRepository.save(incident);
    }

    /** @return 当前数据库中的全部故障事件 */
    public List<Incident> list() {
        return incidentRepository.findAll();
    }

    /**
     * 按 id 查询故障，不存在时统一抛出可转换为 HTTP 400 的业务异常。
     *
     * @param id 故障事件 id
     * @return 匹配的故障实体
     */
    public Incident getById(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("故障事件不存在: " + id));
    }
}
