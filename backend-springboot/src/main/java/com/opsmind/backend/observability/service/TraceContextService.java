package com.opsmind.backend.observability.service;

import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

/**
 * 提供当前 OpenTelemetry 业务链路 id。
 *
 * <p>HTTP 请求处于 Micrometer Span 中时直接复用标准 32 位 traceId；少数没有 Span 的
 * 内部调用生成兼容格式的 id，使持久化记录始终可关联。
 */
@Service
public class TraceContextService {

    /** Micrometer 提供的当前 Span 访问入口。 */
    private final Tracer tracer;

    /** 由 Spring 注入 OpenTelemetry 桥接后的 Tracer。 */
    public TraceContextService(Tracer tracer) {
        this.tracer = tracer;
    }

    /** @return 当前 Span 的 traceId，缺少 Span 时返回新的 32 位十六进制 id */
    public String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
