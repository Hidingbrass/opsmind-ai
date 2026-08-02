package com.opsmind.backend.diagnosis.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "diagnosis_records")
public class DiagnosisRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String incidentId;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, length = 1000)
    private String rootCause;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private Instant createdAt;

    protected DiagnosisRecord() {
    }

    public DiagnosisRecord(
            String incidentId,
            String summary,
            String rootCause,
            String evidenceJson,
            String recommendation,
            double confidence
    ) {
        this.incidentId = incidentId;
        this.summary = summary;
        this.rootCause = rootCause;
        this.evidenceJson = evidenceJson;
        this.recommendation = recommendation;
        this.confidence = confidence;
    }

    @PrePersist
    void prePersist() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public String getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public double getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}