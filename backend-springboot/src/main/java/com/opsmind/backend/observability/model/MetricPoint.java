package com.opsmind.backend.observability.model;

import java.time.Instant;

/**
 * 某一时刻的监控指标样本，用来衡量延迟、错误率和连接错误等异常程度。
 *
 * @param timestamp 指标采样时间
 * @param serviceName 指标所属服务
 * @param metricName 稳定的指标名
 * @param value 指标数值
 * @param unit ms、percent 或 count 等单位
 */
public record MetricPoint(
        Instant timestamp,
        String serviceName,
        String metricName,
        double value,
        String unit
) {
}
