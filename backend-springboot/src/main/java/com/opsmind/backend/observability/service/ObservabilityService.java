package com.opsmind.backend.observability.service;

import java.time.Instant;
import java.util.List;

import com.opsmind.backend.observability.model.DeploymentRecord;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import org.springframework.stereotype.Service;

/**
 * 可观测数据查询服务。
 *
 * <p>MVP 使用固定内存数据让故障可复现；同一组方法同时被普通 HTTP 接口、
 * DiagnosisService 和 ToolGatewayService 复用，后续可将内部实现替换为 Loki、Prometheus 或追踪存储。
 */
@Service
public class ObservabilityService {

    /** 三个演示故障场景的固定日志数据。 */
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
            ),
            new LogEntry(
                    Instant.parse("2026-07-05T10:00:05Z"),
                    "cache-service",
                    "ERROR",
                    "trace-redis-connection-failure-001",
                    "Redis connection refused，缓存读取失败"
            ),
            new LogEntry(
                    Instant.parse("2026-07-05T10:10:01Z"),
                    "order-service",
                    "WARN",
                    "trace-database-slow-query-001",
                    "订单列表查询出现 slow query，SQL 执行耗时过高"
            ));

    /** 与演示故障匹配的延迟、错误率和连接指标。 */
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
            ),
            new MetricPoint(
                    Instant.parse("2026-07-05T10:00:00Z"),
                    "cache-service",
                    "redis.connection.errors",
                    42,
                    "count"
            ),
            new MetricPoint(
                    Instant.parse("2026-07-05T10:10:00Z"),
                    "order-service",
                    "db.query.latency.p95",
                    4200,
                    "ms"
            ));

    /** 与日志 traceId 对应的分布式调用链节点。 */
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
            ),
            new TraceSpan(
                    "trace-redis-connection-failure-001",
                    "span-cache-get",
                    null,
                    "cache-service",
                    "GET redis://order:detail",
                    1800,
                    "ERROR",
                    "Redis connection refused"
            ),
            new TraceSpan(
                    "trace-database-slow-query-001",
                    "span-mysql-orders-query",
                    null,
                    "mysql",
                    "SELECT orders by user_id order by created_at",
                    4200,
                    "ERROR",
                    "slow query, full table scan"
            ));

    /** 与三个演示服务相匹配的最近发布记录，用于排查变更相关性。 */
    private final List<DeploymentRecord> deployments = List.of(
            new DeploymentRecord(
                    Instant.parse("2026-07-05T08:30:00Z"),
                    "payment-service",
                    "2.4.1",
                    "8fd31a2",
                    "release-bot",
                    "SUCCESS",
                    "调整支付网关连接与重试参数"
            ),
            new DeploymentRecord(
                    Instant.parse("2026-07-04T16:00:00Z"),
                    "cache-service",
                    "1.8.0",
                    "14be0cd",
                    "release-bot",
                    "SUCCESS",
                    "升级 Redis 客户端并调整连接池配置"
            ),
            new DeploymentRecord(
                    Instant.parse("2026-07-05T09:40:00Z"),
                    "order-service",
                    "3.2.0",
                    "cd901f4",
                    "release-bot",
                    "SUCCESS",
                    "上线订单列表筛选与排序功能"
            )
    );

    /**
     * 按服务名返回日志，目前也是 queryLogs Tool 的实际执行方法。
     *
     * @param serviceName 要查询的服务名
     * @return 匹配日志，没有数据时返回空列表
     */
    public List<LogEntry> queryLogs(String serviceName) {
        return logs.stream()
                .filter(log -> log.serviceName().equals(serviceName))
                .toList();
    }

    /**
     * 按服务名返回监控指标。
     *
     * @param serviceName 要查询的服务名
     * @return 匹配的指标样本
     */
    public List<MetricPoint> queryMetrics(String serviceName) {
        return metrics.stream()
                .filter(metric -> metric.serviceName().equals(serviceName))
                .toList();
    }

    /**
     * 返回属于同一 traceId 的全部链路节点。
     *
     * @param traceId 日志中取得的追踪 id
     * @return 按内存数据顺序返回的链路节点
     */
    public List<TraceSpan> queryTrace(String traceId) {
        return traces.stream()
                .filter(span -> span.traceId().equals(traceId))
                .toList();
    }

    /**
     * 查询某服务最近的发布记录，当前内存数据按时间倒序返回。
     *
     * @param serviceName 要排查的服务名
     * @return 最近发布记录
     */
    public List<DeploymentRecord> getRecentDeployments(String serviceName) {
        return deployments.stream()
                .filter(deployment -> deployment.serviceName().equals(serviceName))
                .sorted((left, right) -> right.deployedAt().compareTo(left.deployedAt()))
                .toList();
    }
}
