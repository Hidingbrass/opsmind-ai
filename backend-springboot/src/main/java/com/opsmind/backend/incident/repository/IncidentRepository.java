package com.opsmind.backend.incident.repository;

import com.opsmind.backend.incident.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, String> {
}
