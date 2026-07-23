package com.opsmind.backend.tool.service;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.backend.tool.dto.ToolCallAuditResponse;
import com.opsmind.backend.tool.dto.ToolExecutionRequest;
import com.opsmind.backend.tool.dto.ToolExecutionResponse;
import com.opsmind.backend.tool.dto.ToolExecutionStatus;
import com.opsmind.backend.tool.model.ToolCallAudit;
import com.opsmind.backend.tool.model.ToolCallStatus;
import com.opsmind.backend.tool.repository.ToolCallAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将 Tool Gateway 的最终执行结果转换成审计记录并保存。
 *
 * <p>该服务不执行工具，也不改变工具响应；它只负责审计这一项旁路能力。
 */
@Service
public class ToolCallAuditService {
    /**
     * 审计保存失败时记录内部预警，不把审计异常返回给 AI Agent。
     */
    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditService.class);
    /**
     * 提供工具调用审计的数据库保存和查询能力。
     */
    private final ToolCallAuditRepository toolCallAuditRepository;
    /**
     * 将动态工具请求序列化成可持久化的 JSON。
     */
    private final ObjectMapper objectMapper;

    /**
     * 由 Spring 注入持久化仓库和统一 JSON 工具。
     */
    public ToolCallAuditService(
            ToolCallAuditRepository toolCallAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.toolCallAuditRepository = toolCallAuditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据原始请求和最终工具响应尽力保存一条审计记录。
     *
     * @param request  AI Agent 发来的工具调用请求
     * @param response Tool Gateway 已经确定的成功或失败响应
     */
    public void record(ToolExecutionRequest request, ToolExecutionResponse response) {
        String responseSummary = null;
        ToolCallStatus auditStatus;
        try {
            String json = objectMapper.writeValueAsString(request);
            // 非法请求可能缺少上下文；保留 null 以便失败调用仍然可以写入审计表。
            String taskId = request == null ? null : request.taskId();
            String incidentId = request == null ? null : request.incidentId();
            // toolName 是数据库非空字段，缺失时使用 unknown 标识无法识别的工具调用。
            String toolName = response.toolName() == null || response.toolName().isBlank()
                    ? "unknown"
                    : response.toolName();

            // 审计状态和摘要都从最终响应派生，避免与 Agent 实际收到的结果不一致。
            if (response.status() == ToolExecutionStatus.SUCCESS) {
                auditStatus = ToolCallStatus.SUCCESS;
                if (response.data() instanceof List<?> list) {
                    responseSummary = "返回 " + list.size() + " 条记录";
                } else {
                    responseSummary = "工具执行成功";
                }
            } else {
                auditStatus = ToolCallStatus.FAILED;
            }

            // 只保存结果摘要和失败原因，不把日志正文等大对象重复写入审计表。
            ToolCallAudit audit = new ToolCallAudit(
                    taskId,
                    incidentId,
                    toolName,
                    json,
                    responseSummary,
                    auditStatus,
                    response.latencyMs(),
                    response.errorMessage()
            );
            toolCallAuditRepository.save(audit);

        } catch (Exception exception) {
            log.warn("保存工具调用审计失败", exception);
        }
    }

    /**
     * 按诊断任务查询审计历史，并转换成不含原始请求参数的对外 DTO。
     *
     * @param taskId 异步诊断任务 id
     * @return 最新工具调用排在前面的审计列表
     */
    public List<ToolCallAuditResponse> listByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }

        // Repository 负责筛选和排序，Service 只负责把实体转换成安全的接口视图。
        return toolCallAuditRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(ToolCallAuditResponse::from)
                .toList();
    }
}
