package com.opsmind.backend.observability.model;

import java.time.Instant;

/**
 * 一条结构化服务日志，用来回答“在什么时间发生了什么”。
 *
 * @param timestamp 日志发生时间
 * @param serviceName 产生日志的服务
 * @param level INFO、WARN 或 ERROR 等级别
 * @param traceId 关联同一请求链路的追踪 id
 * @param message 日志正文
 */
public record LogEntry(
        Instant timestamp,
        String serviceName,
        String level,
        String traceId,
        String message
) {
}
