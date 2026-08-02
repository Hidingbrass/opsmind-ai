package com.opsmind.backend.observability.service;

import java.time.Instant;
import java.util.List;

import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import org.springframework.stereotype.Service;

@Service
public class ObservabilityService {

    private final List<LogEntry> logs = List.of(
            new LogEntry(
                    Instant.parse("2026-07-05T10:00:01Z"),
                    "order-service",
                    "INFO",
                    "trace-payment-timeout-001",
                    "收到订单结算请求，开始调用支付服务"
            ),
            new LogEntry(
                    Instant.parse("2026-07-05T10:00:03Z"),
                    "payment-service",
                    "ERROR",
                    "trace-payment-timeout-001",
                    "调用第三方支付网关超时，timeout=5000ms"
            ),
            new LogEntry(
                    Instant.parse("2026-07-05T10:00:04Z"),
                    "payment-service",
                    "ERROR",
                    "trace-payment-timeout-001",
                    "支付请求失败，返回 PAYMENT_GATEWAY_TIMEOUT"
            ));

    private final List<MetricPoint> metrics = List.of(
            new MetricPoint(
                    Instant.parse("2026-07-05T10:00:00Z"),
                    "payment-service",
                    "http.server.requests.p95",
                    5200,
                    "ms"
            ),
            new MetricPoint(
                    Instant.parse("2026-07-05T10:00:00Z"),
                    "payment-service",
                    "http.server.error.rate",
                    18,
                    "percent"
            ),
            new MetricPoint(
                    Instant.parse("2026-07-05T10:00:00Z"),
                    "order-service",
                    "checkout.latency.p95",
                    5600,
                    "ms"
            ));

    private final List<TraceSpan> traces = List.of(
            new TraceSpan(
                    "trace-payment-timeout-001",
                    "span-order-checkout",
                    null,
                    "order-service",
                    "POST /api/orders/checkout",
                    5600,
                    "ERROR",
                    "下游支付服务超时"
            ),
            new TraceSpan(
                    "trace-payment-timeout-001",
                    "span-payment-pay",
                    "span-order-checkout",
                    "payment-service",
                    "POST /api/payments/pay",
                    5200,
                    "ERROR",
                    "调用第三方支付网关超时"
            ),
            new TraceSpan(
                    "trace-payment-timeout-001",
                    "span-gateway-charge",
                    "span-payment-pay",
                    "payment-gateway",
                    "POST /charge",
                    5000,
                    "ERROR",
                    "gateway timeout"
            ));

    public List<LogEntry> queryLogs(String serviceName) {
        return logs.stream()
                .filter(log -> log.serviceName().equals(serviceName))
                .toList();
    }

    public List<MetricPoint> queryMetrics(String serviceName) {
        return metrics.stream()
                .filter(metric -> metric.serviceName().equals(serviceName))
                .toList();
    }

    public List<TraceSpan> queryTrace(String traceId) {
        return traces.stream()
                .filter(span -> span.traceId().equals(traceId))
                .toList();
    }
}
