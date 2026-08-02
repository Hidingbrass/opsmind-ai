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

/**
 * 管理可演示的固定故障场景，并将场景注入转换为 Incident 模块中的真实故障事件。
 */
@Service
public class FaultScenarioService {

    /** 复用 Incident 模块的创建逻辑，避免 Fault 模块跨层操作数据库。 */
    private final IncidentService incidentService;

    /** 当前用于 MVP 演示的内存故障场景集合。 */
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

    /** @param incidentService 故障事件业务服务 */
    public FaultScenarioService(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /** @return 所有可注入的预置故障场景 */
    public List<FaultScenario> listScenarios() {
        return scenarios;
    }

    /**
     * 按场景键查找模板，调用 IncidentService 创建一条可继续诊断的故障。
     *
     * @param scenarioKey 预置场景唯一键
     * @return 场景信息和新故障事件
     */
    public FaultInjectionResponse inject(String scenarioKey) {
        // 先确认场景存在，再创建 Incident，避免产生来源不明的故障数据。
        FaultScenario scenario = scenarios.stream()
                .filter(item -> item.key().equals(scenarioKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("故障场景不存在: " + scenarioKey));

        // Fault 模块只负责选场景，持久化规则仍由 IncidentService 统一管理。
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
