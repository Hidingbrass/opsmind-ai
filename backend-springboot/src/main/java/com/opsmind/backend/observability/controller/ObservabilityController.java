package com.opsmind.backend.observability.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.observability.model.LogEntry;
import com.opsmind.backend.observability.model.MetricPoint;
import com.opsmind.backend.observability.model.TraceSpan;
import com.opsmind.backend.observability.service.ObservabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 将日志、指标和 Trace 查询能力暴露为可独立验证的 HTTP API。 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    /** 可观测数据查询服务。 */
    private final ObservabilityService observabilityService;

    /** @param observabilityService 由 Spring 注入的可观测服务 */
    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    /**
     * @param serviceName 查询参数中的服务名
     * @return 该服务的日志列表
     */
    @GetMapping("/logs")
    public Result<List<LogEntry>> queryLogs(@RequestParam String serviceName) {
        return Result.success(observabilityService.queryLogs(serviceName));
    }

    /**
     * @param serviceName 查询参数中的服务名
     * @return 该服务的指标列表
     */
    @GetMapping("/metrics")
    public Result<List<MetricPoint>> queryMetrics(@RequestParam String serviceName) {
        return Result.success(observabilityService.queryMetrics(serviceName));
    }

    /**
     * @param traceId URL 路径中的追踪 id
     * @return 完整调用链节点列表
     */
    @GetMapping("/traces/{traceId}")
    public Result<List<TraceSpan>> queryTrace(@PathVariable String traceId) {
        return Result.success(observabilityService.queryTrace(traceId));
    }
}
