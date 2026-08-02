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

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping("/logs")
    public Result<List<LogEntry>> queryLogs(@RequestParam String serviceName) {
        return Result.success(observabilityService.queryLogs(serviceName));
    }

    @GetMapping("/metrics")
    public Result<List<MetricPoint>> queryMetrics(@RequestParam String serviceName) {
        return Result.success(observabilityService.queryMetrics(serviceName));
    }

    @GetMapping("/traces/{traceId}")
    public Result<List<TraceSpan>> queryTrace(@PathVariable String traceId) {
        return Result.success(observabilityService.queryTrace(traceId));
    }
}
