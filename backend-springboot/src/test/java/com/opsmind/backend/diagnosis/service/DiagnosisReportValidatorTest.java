package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import org.junit.jupiter.api.Test;

class DiagnosisReportValidatorTest {

    @Test
    void rejectsOversizedAiReportBeforePersistence() {
        DiagnosisReport report = new DiagnosisReport(
                "incident-1",
                "0".repeat(32),
                "x".repeat(1001),
                "root cause",
                List.of("evidence"),
                "retry",
                0.5,
                null
        );

        assertThatThrownBy(() -> DiagnosisReportValidator.validate(report, "incident-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }
}
