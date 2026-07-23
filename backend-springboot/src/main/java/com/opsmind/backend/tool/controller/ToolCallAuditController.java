package com.opsmind.backend.tool.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.tool.dto.ToolCallAuditResponse;
import com.opsmind.backend.tool.service.ToolCallAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具调用审计的只读查询入口。
 *
 * <p>Controller 不读取 JPA 实体，也不处理排序和脱敏，只把 taskId 交给审计 Service。
 */
@RestController
@RequestMapping("/api/tool-call-audits")
public class ToolCallAuditController {

    /** 查询审计历史并转换成安全 DTO 的业务服务。 */
    private final ToolCallAuditService toolCallAuditService;

    /** @param toolCallAuditService 由 Spring 注入的工具审计服务 */
    public ToolCallAuditController(ToolCallAuditService toolCallAuditService) {
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * @param taskId 查询参数中的异步诊断任务 id
     * @return 最新工具调用排在前面的审计列表
     */
    @GetMapping
    public Result<List<ToolCallAuditResponse>> listByTaskId(@RequestParam String taskId) {
        return Result.success(toolCallAuditService.listByTaskId(taskId));
    }
}
