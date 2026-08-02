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

@RestController
@RequestMapping("/api/fault-scenarios")
public class FaultScenarioController {

    private final FaultScenarioService faultScenarioService;

    public FaultScenarioController(FaultScenarioService faultScenarioService) {
        this.faultScenarioService = faultScenarioService;
    }

    @GetMapping
    public Result<List<FaultScenarioResponse>> listScenarios() {
        List<FaultScenarioResponse> scenarios = faultScenarioService.listScenarios().stream()
                .map(FaultScenarioResponse::from)
                .toList();
        return Result.success(scenarios);
    }

    @PostMapping("/{scenarioKey}/inject")
    public Result<FaultInjectionResponse> inject(@PathVariable String scenarioKey) {
        return Result.success(faultScenarioService.inject(scenarioKey));
    }
}
