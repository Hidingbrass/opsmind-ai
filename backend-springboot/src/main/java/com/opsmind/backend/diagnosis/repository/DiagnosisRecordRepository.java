package com.opsmind.backend.diagnosis.repository;

import java.util.List;

import com.opsmind.backend.diagnosis.model.DiagnosisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRecordRepository extends JpaRepository<DiagnosisRecord, String> {

    List<DiagnosisRecord> findByIncidentIdOrderByCreatedAtDesc(String incidentId);
}
