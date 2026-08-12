package com.opsmind.backend.incident.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.incident.dto.CreateIncidentRequest;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障事件 HTTP 入口，只负责参数绑定、调用 Service 和转换响应 DTO。
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    /** 故障事件业务服务。 */
    private final IncidentService incidentService;

    /** @param incidentService 由 Spring 注入的故障业务服务 */
    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * 接收一条故障创建请求并返回已入库的事件快照。
     *
     * @param request HTTP JSON 请求体
     * @return 统一响应包装的故障事件
     */
    @PostMapping
    public Result<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        Incident incident = incidentService.create(request);
        return Result.success(IncidentResponse.from(incident));
    }

    /** @return 统一响应包装的全部故障事件列表 */
    @GetMapping
    public Result<List<IncidentResponse>> list() {
        List<IncidentResponse> incidents = incidentService.list().stream()
                .map(IncidentResponse::from)
                .toList();
        return Result.success(incidents);
    }

    /**
     * 查询单个故障详情。
     *
     * @param id URL 路径中的故障事件 id
     * @return 匹配的故障事件快照
     */
    @GetMapping("/{id}")
    public Result<IncidentResponse> getById(@PathVariable String id) {
        Incident incident = incidentService.getById(id);
        return Result.success(IncidentResponse.from(incident));
    }
}
