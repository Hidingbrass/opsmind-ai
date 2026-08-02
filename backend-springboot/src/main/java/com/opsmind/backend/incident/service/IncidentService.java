package com.opsmind.backend.incident.service;

import java.util.List;

import com.opsmind.backend.incident.dto.CreateIncidentRequest;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public Incident create(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setServiceName(request.serviceName());
        incident.setSeverity(request.severity());
        incident.setSymptom(request.symptom());
        return incidentRepository.save(incident);
    }

    public List<Incident> list() {
        return incidentRepository.findAll();
    }

    public Incident getById(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("故障事件不存在: " + id));
    }
}
