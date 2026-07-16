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

/**
 * 同步诊断和诊断历史的 HTTP 入口。
 *
 * <p>同步接口保留用于基础闭环和调试；用户体验主链路使用 DiagnosisTaskController 的异步接口。
 */
@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {
    /** 真正收集证据、调用 AI 并保存报告的服务。 */
    private final DiagnosisService diagnosisService;

    /** @param diagnosisService 诊断核心服务 */
    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * 同步执行诊断，HTTP 请求会等待 AI 服务完成。
     *
     * @param incidentId URL 中的故障 id
     * @return 已保存的诊断报告
     */
    @PostMapping("/incidents/{incidentId}")
    public Result<DiagnosisReport> diagnose(@PathVariable String incidentId) {
        DiagnosisReport report = diagnosisService.diagnose(incidentId);
        return Result.success(report);
    }

    /**
     * @param incidentId 故障事件 id
     * @return 该故障的全部历史诊断，最新记录在前
     */
    @GetMapping("/incidents/{incidentId}/records")
    public Result<List<DiagnosisRecordResponse>> listRecords(@PathVariable String incidentId) {
        return Result.success(diagnosisService.listRecords(incidentId));
    }
}
