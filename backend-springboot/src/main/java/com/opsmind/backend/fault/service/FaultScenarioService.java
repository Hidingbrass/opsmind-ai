package com.opsmind.backend.fault.service;

import java.util.List;

import com.opsmind.backend.fault.dto.FaultInjectionResponse;
import com.opsmind.backend.fault.model.FaultScenario;
import com.opsmind.backend.incident.dto.CreateIncidentRequest;
import com.opsmind.backend.incident.dto.IncidentResponse;
import com.opsmind.backend.incident.model.Incident;
import com.opsmind.backend.incident.model.IncidentSeverity;
import com.opsmind.backend.incident.service.IncidentService;
import org.springframework.stereotype.Service;

@Service
public class FaultScenarioService {

    private final IncidentService incidentService;

    private final List<FaultScenario> scenarios = List.of(
            new FaultScenario(
                    "payment-timeout",
                    "支付服务超时导致订单结算失败",
                    "payment-service",
                    IncidentSeverity.HIGH,
                    "用户提交订单后等待超过 5 秒，最终返回支付服务超时错误",
                    "支付服务调用第三方支付网关响应超时",
                    "检查支付网关状态，必要时切换备用通道或启用降级策略"
            ),
            new FaultScenario(
                    "redis-connection-failure",
                    "Redis 连接失败导致缓存不可用",
                    "cache-service",
                    IncidentSeverity.MEDIUM,
                    "订单查询接口响应变慢，日志中出现 Redis 连接失败",
                    "缓存服务连接池耗尽或 Redis 实例不可达",
                    "检查 Redis 实例状态、连接池配置和网络连通性"
            ),
            new FaultScenario(
                    "database-slow-query",
                    "数据库慢查询导致订单接口延迟升高",
                    "order-service",
                    IncidentSeverity.HIGH,
                    "订单列表接口响应时间从 200ms 上升到 3s 以上",
                    "订单查询缺少合适索引，导致高峰期出现全表扫描",
                    "分析慢 SQL，补充索引并评估分页查询策略"
            )
    );

    public FaultScenarioService(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    public List<FaultScenario> listScenarios() {
        return scenarios;
    }

    public FaultInjectionResponse inject(String scenarioKey) {
        FaultScenario scenario = scenarios.stream()
                .filter(item -> item.key().equals(scenarioKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("故障场景不存在: " + scenarioKey));

        Incident incident = incidentService.create(new CreateIncidentRequest(
                scenario.title(),
                scenario.serviceName(),
                scenario.severity(),
                scenario.symptom()
        ));

        return new FaultInjectionResponse(
                scenario.key(),
                "已注入故障场景: " + scenario.title(),
                IncidentResponse.from(incident)
        );
    }
}
