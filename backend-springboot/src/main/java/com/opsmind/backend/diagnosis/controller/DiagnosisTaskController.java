package com.opsmind.backend.diagnosis.controller;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 异步诊断任务的创建、查询和 SSE 订阅入口。 */
@RestController
@RequestMapping("/api/diagnosis-tasks")
public class DiagnosisTaskController {
    /** 诊断任务编排服务。 */
    private final DiagnosisTaskService diagnosisTaskService;

    /** @param diagnosisTaskService 由 Spring 注入的任务服务 */
    public DiagnosisTaskController(DiagnosisTaskService diagnosisTaskService) {
        this.diagnosisTaskService = diagnosisTaskService;
    }

    /**
     * 创建 PENDING 任务并触发后台执行，不等待 AI 返回。
     *
     * @param incidentId 要诊断的故障 id
     * @return 包含 taskId 的任务快照
     */
    @PostMapping("/incidents/{incidentId}")
    public Result<DiagnosisTaskResponse> createTask(
            @PathVariable String incidentId,
            HttpServletRequest request
    ) {
        return Result.success(
                diagnosisTaskService.createTask(incidentId, resolveClientKey(request))
        );
    }

    /** @return 数据库中当前任务状态，可作为 SSE 的可靠兜底 */
    @GetMapping("/{taskId}")
    public Result<DiagnosisTaskResponse> getTask(@PathVariable String taskId) {
        return Result.success(diagnosisTaskService.getTask(taskId));
    }

    /**
     * 查询某个故障最近一次诊断任务。没有历史任务时 data 为 null，而不是返回 404。
     */
    @GetMapping
    public Result<DiagnosisTaskResponse> getLatestTask(
            @RequestParam String incidentId
    ) {
        return Result.success(
                diagnosisTaskService.getLatestTaskForIncident(incidentId).orElse(null)
        );
    }

    /**
     * 建立 {@code text/event-stream} 长连接。SSE 不使用 Result 包装，因为响应需要持续保持打开。
     *
     * @param taskId 要订阅的诊断任务 id
     * @return Spring MVC 管理的 SSE 连接对象
     */
    @GetMapping(
            value = "/{taskId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribeEvents(@PathVariable String taskId) {
        return diagnosisTaskService.subscribeEvents(taskId);
    }

    /** 优先使用反向代理传递的客户端地址，为 Redis 限流生成稳定键。 */
    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
