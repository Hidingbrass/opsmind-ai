package com.opsmind.backend.diagnosis.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.diagnosis.dto.AiCallAuditResponse;
import com.opsmind.backend.diagnosis.service.AiCallAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 调用尝试的只读审计接口。 */
@RestController
@RequestMapping("/api/ai-call-audits")
public class AiCallAuditController {

    /** 查询 AI 服务调用审计的业务服务。 */
    private final AiCallAuditService aiCallAuditService;

    /** @param aiCallAuditService 由 Spring 注入的 AI 调用审计服务 */
    public AiCallAuditController(AiCallAuditService aiCallAuditService) {
        this.aiCallAuditService = aiCallAuditService;
    }

    /** @return 指定任务的 AI 服务调用记录，最新记录排在最前面 */
    @GetMapping
    public Result<List<AiCallAuditResponse>> listByTaskId(@RequestParam String taskId) {
        return Result.success(aiCallAuditService.listByTaskId(taskId));
    }
}
