package com.opsmind.backend.tool.dto;

import java.time.Instant;

import com.opsmind.backend.tool.model.ToolCallAudit;
import com.opsmind.backend.tool.model.ToolCallStatus;

/**
 * 工具调用审计的对外查询视图。
 *
 * <p>DTO 只暴露前端展示所需字段，隐藏可能包含动态参数的 requestPayload，
 * 同时避免 HTTP 接口直接依赖 JPA 实体结构。
 *
 * @param id 审计记录 id
 * @param taskId 关联诊断任务 id
 * @param incidentId 关联故障事件 id
 * @param toolName 实际尝试执行的工具名
 * @param responseSummary 成功时的结果摘要，不包含完整工具响应
 * @param status 工具执行成功或失败
 * @param latencyMs 工具调用耗时，单位为毫秒
 * @param errorMessage 失败原因，成功时为 null
 * @param createdAt 审计记录入库时间
 */
public record ToolCallAuditResponse(
        String id,
        String taskId,
        String incidentId,
        String toolName,
        String responseSummary,
        ToolCallStatus status,
        long latencyMs,
        String errorMessage,
        Instant createdAt
) {
    /**
     * 将数据库实体转换成安全的接口响应对象。
     *
     * @param audit Repository 查询到的审计实体
     * @return 不包含 requestPayload 的前端视图
     */
    public static ToolCallAuditResponse from(ToolCallAudit audit) {
        return new ToolCallAuditResponse(
                audit.getId(),
                audit.getTaskId(),
                audit.getIncidentId(),
                audit.getToolName(),
                audit.getResponseSummary(),
                audit.getStatus(),
                audit.getLatencyMs(),
                audit.getErrorMessage(),
                audit.getCreatedAt()
        );
    }
}
