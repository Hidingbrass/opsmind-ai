package com.opsmind.backend.observability.model;

/**
 * 分布式调用链中的一个执行节点，用来回答“请求在哪个服务或下游节点变慢/失败”。
 *
 * @param traceId 整条请求链路 id
 * @param spanId 当前节点 id
 * @param parentSpanId 父节点 id，根节点为 null
 * @param serviceName 执行该节点的服务
 * @param operationName HTTP 接口、SQL 或其他操作名
 * @param durationMs 节点耗时，单位毫秒
 * @param status 节点成功或失败状态
 * @param errorMessage 节点失败原因，成功时可为 null
 */
public record TraceSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String serviceName,
        String operationName,
        long durationMs,
        String status,
        String errorMessage
) {
}
