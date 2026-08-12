package com.opsmind.backend.diagnosis.service;

import java.util.List;

import com.opsmind.backend.diagnosis.dto.AiCallAuditResponse;
import com.opsmind.backend.diagnosis.dto.AgentExecutionMetadata;
import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.diagnosis.model.AiCallAudit;
import com.opsmind.backend.diagnosis.model.AiCallStatus;
import com.opsmind.backend.diagnosis.repository.AiCallAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 保存和查询 Spring→Python AI 调用尝试，审计失败不反向影响诊断。 */
@Service
public class AiCallAuditService {

    /** 审计旁路保存失败时只记录后端预警。 */
    private static final Logger log = LoggerFactory.getLogger(AiCallAuditService.class);
    /** AI 调用审计的 MySQL 持久化入口。 */
    private final AiCallAuditRepository repository;

    /** 由 Spring 注入审计仓库。 */
    public AiCallAuditService(AiCallAuditRepository repository) {
        this.repository = repository;
    }

    /** 尽力保存一次 Spring 到 Python 调用，审计失败只告警而不改变诊断结果。 */
    public void record(
            DiagnosisRequest request,
            DiagnosisReport report,
            boolean successful,
            long latencyMs,
            Throwable error
    ) {
        try {
            AgentExecutionMetadata metadata;
            if (report == null) {
                metadata = new AgentExecutionMetadata(
                        "UNKNOWN",
                        "opsmind-python-agent",
                        "unknown",
                        "unknown",
                        0,
                        0,
                        0
                );
            } else if (report.agentMetadata() == null) {
                metadata = AgentExecutionMetadata.deterministic();
            } else {
                metadata = report.agentMetadata();
            }
            repository.save(new AiCallAudit(
                    request.taskId(),
                    request.incident().id(),
                    request.traceId(),
                    metadata.provider(),
                    metadata.modelName(),
                    metadata.executionMode(),
                    metadata.promptVersion(),
                    metadata.inputTokens(),
                    metadata.outputTokens(),
                    metadata.toolCallCount(),
                    successful ? AiCallStatus.SUCCESS : AiCallStatus.FAILED,
                    latencyMs,
                    error == null ? null : safeMessage(error)
            ));
        } catch (RuntimeException exception) {
            log.warn("保存 AI 调用审计失败", exception);
        }
    }

    /** @return 指定任务的 AI 服务调用记录，最新记录排在最前面 */
    public List<AiCallAuditResponse> listByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(AiCallAuditResponse::from)
                .toList();
    }

    /** 对外只保存异常短消息，不保存堆栈。 */
    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
