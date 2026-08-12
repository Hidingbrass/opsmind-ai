package com.opsmind.backend.tool.controller;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.tool.dto.ToolExecutionRequest;
import com.opsmind.backend.tool.dto.ToolExecutionResponse;
import com.opsmind.backend.tool.service.ToolGatewayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Agent 和 curl 调用工具的统一 HTTP 入口。
 *
 * <p>Controller 不执行具体工具，只将结构化请求交给 ToolGatewayService。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolGatewayController {
    /** 负责参数校验、工具分发和结果包装的网关服务。 */
    private final ToolGatewayService toolGatewayService;

    /** @param toolGatewayService 由 Spring 注入的工具网关 */
    public ToolGatewayController(ToolGatewayService toolGatewayService) {
        this.toolGatewayService = toolGatewayService;
    }

    /**
     * 使用 POST 接收带动态 arguments 的 JSON，工具失败也会以结构化 data 返回给 Agent。
     *
     * @param request 工具名、诊断上下文和动态参数
     * @return 统一 HTTP 响应中包装的工具级结果
     */
    @PostMapping("/execute")
    public Result<ToolExecutionResponse> execute(
            @Valid @RequestBody ToolExecutionRequest request
    ) {
        return Result.success(toolGatewayService.execute(request));
    }
}
