package com.opsmind.backend.incident.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.incident.dto.CreateIncidentRequest;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.service.IncidentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public Result<IncidentResponse> create(@RequestBody CreateIncidentRequest request) {
        Incident incident = incidentService.create(request);
        return Result.success(IncidentResponse.from(incident));
    }

    @GetMapping
    public Result<List<IncidentResponse>> list() {
        List<IncidentResponse> incidents = incidentService.list().stream()
                .map(IncidentResponse::from)
                .toList();
        return Result.success(incidents);
    }

    @GetMapping("/{id}")
    public Result<IncidentResponse> getById(@PathVariable String id) {
        Incident incident = incidentService.getById(id);
        return Result.success(IncidentResponse.from(incident));
    }
}
