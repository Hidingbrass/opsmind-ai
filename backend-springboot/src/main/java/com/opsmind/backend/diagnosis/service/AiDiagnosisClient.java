package com.opsmind.backend.diagnosis.service;

import java.time.Duration;

import com.opsmind.backend.diagnosis.dto.DiagnosisReport;
import com.opsmind.backend.diagnosis.dto.DiagnosisRequest;
import com.opsmind.backend.observability.service.OpsMindMetrics;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Spring 调用 Python AI 服务的稳定性边界。
 *
 * <p>RestClient 提供连接与读取超时，Resilience4j 负责有限重试、并发隔离和熔断；
 * 最终失败会转换成可写入诊断任务 failureReason 的明确异常。
 */
@Service
public class AiDiagnosisClient {

    /** application.yml 中重试、熔断和 Bulkhead 共用的实例名。 */
    private static final String RESILIENCE_INSTANCE = "aiService";

    /** 由 Spring Boot 配置、能自动传播 traceparent 的 HTTP 客户端。 */
    private final RestClient restClient;
    /** Python AI 服务根地址，本地和 Docker 环境可以分别覆盖。 */
    private final String aiBaseUrl;
    /** 记录 AI 服务成功率和调用耗时。 */
    private final OpsMindMetrics opsMindMetrics;
    /** 将每次 Spring -> Python 调用结果保存到 MySQL。 */
    private final AiCallAuditService aiCallAuditService;

    /** 由 Spring 注入 HTTP、配置、指标和审计依赖。 */
    public AiDiagnosisClient(
            RestClient restClient,
            @Value("${opsmind.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            OpsMindMetrics opsMindMetrics,
            AiCallAuditService aiCallAuditService
    ) {
        this.restClient = restClient;
        this.aiBaseUrl = aiBaseUrl;
        this.opsMindMetrics = opsMindMetrics;
        this.aiCallAuditService = aiCallAuditService;
    }

    /** 调用结构化诊断接口；注解由 Spring AOP 在 Bean 边界应用。 */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "fallback")
    @Retry(name = RESILIENCE_INSTANCE)
    @Bulkhead(name = RESILIENCE_INSTANCE)
    public DiagnosisReport diagnose(DiagnosisRequest request) {
        long startedAt = System.nanoTime();
        try {
            DiagnosisReport report = restClient.post()
                    .uri(aiBaseUrl + "/ai/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DiagnosisReport.class);

            if (report == null) {
                throw new IllegalStateException("AI 服务返回空诊断报告");
            }
            Duration duration = elapsed(startedAt);
            opsMindMetrics.aiCall(true, duration);
            aiCallAuditService.record(request, true, duration.toMillis(), null);
            return report;
        } catch (RuntimeException exception) {
            Duration duration = elapsed(startedAt);
            opsMindMetrics.aiCall(false, duration);
            aiCallAuditService.record(request, false, duration.toMillis(), exception);
            throw exception;
        }
    }

    /** 重试耗尽、熔断或并发隔离拒绝后返回统一的友好失败边界。 */
    private DiagnosisReport fallback(DiagnosisRequest request, Throwable throwable) {
        throw new AiServiceUnavailableException(
                "AI 诊断服务暂时不可用，请稍后重试",
                throwable
        );
    }

    /** 使用单调时钟计算耗时，避免系统时间校准影响统计。 */
    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
