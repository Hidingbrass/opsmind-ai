package com.opsmind.backend.fault.dto;

import com.opsmind.backend.fault.model.FaultScenario;
import com.opsmind.backend.incident.model.IncidentSeverity;

/**
 * 场景列表接口对外暴露的简化视图，故意不返回期望根因以免提前泄露答案。
 *
 * @param key 场景唯一键
 * @param title 场景标题
 * @param serviceName 受影响服务
 * @param severity 严重程度
 * @param symptom 可观察故障现象
 */
public record FaultScenarioResponse(
        String key,
        String title,
        String serviceName,
        IncidentSeverity severity,
        String symptom
) {

    /** @return 由内部完整场景转换得到的 API 响应视图 */
    public static FaultScenarioResponse from(FaultScenario scenario) {
        return new FaultScenarioResponse(
                scenario.key(),
                scenario.title(),
                scenario.serviceName(),
                scenario.severity(),
                scenario.symptom()
        );
    }
}
