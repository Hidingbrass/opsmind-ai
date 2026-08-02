package com.opsmind.backend.fault.controller;

import java.util.List;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.fault.dto.FaultInjectionResponse;
import com.opsmind.backend.fault.dto.FaultScenarioResponse;
import com.opsmind.backend.fault.service.FaultScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 故障场景列表与一键注入的 HTTP 入口。 */
@RestController
@RequestMapping("/api/fault-scenarios")
public class FaultScenarioController {

    /** 故障场景编排服务。 */
    private final FaultScenarioService faultScenarioService;

    /** @param faultScenarioService 由 Spring 注入的场景服务 */
    public FaultScenarioController(FaultScenarioService faultScenarioService) {
        this.faultScenarioService = faultScenarioService;
    }

    /** @return 可供页面或 curl 选择的故障场景列表 */
    @GetMapping
    public Result<List<FaultScenarioResponse>> listScenarios() {
        List<FaultScenarioResponse> scenarios = faultScenarioService.listScenarios().stream()
                .map(FaultScenarioResponse::from)
                .toList();
        return Result.success(scenarios);
    }

    /**
     * 注入指定场景并返回新建故障，后续可使用该 incidentId 启动诊断。
     *
     * @param scenarioKey URL 中的预置场景键
     * @return 故障注入结果
     */
    @PostMapping("/{scenarioKey}/inject")
    public Result<FaultInjectionResponse> inject(@PathVariable String scenarioKey) {
        return Result.success(faultScenarioService.inject(scenarioKey));
    }
}
