package com.opsmind.backend.diagnosis.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.diagnosis.dto.DiagnosisRecordResponse;
import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.service.DiagnosisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {
    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @PostMapping("/incidents/{incidentId}")
    public Result<DiagnosisReport> diagnose(@PathVariable String incidentId) {
        DiagnosisReport report = diagnosisService.diagnose(incidentId);
        return Result.success(report);
    }

    @GetMapping("/incidents/{incidentId}/records")
    public Result<List<DiagnosisRecordResponse>> listRecords(@PathVariable String incidentId) {
        return Result.success(diagnosisService.listRecords(incidentId));
    }
}
