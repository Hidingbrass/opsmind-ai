package com.opsmind.backend.incident.dto;

import com.opsmind.backend.incident.model.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建故障事件时的 HTTP 请求体。
 *
 * @param title 故障标题
 * @param serviceName 故障所属服务
 * @param severity 故障严重程度
 * @param symptom 用户或监控系统观察到的现象
 */
public record CreateIncidentRequest(
        @NotBlank(message = "故障标题不能为空")
        @Size(max = 120, message = "故障标题不能超过 120 个字符")
        String title,
        @NotBlank(message = "服务名不能为空")
        @Size(max = 80, message = "服务名不能超过 80 个字符")
        String serviceName,
        @NotNull(message = "故障级别不能为空")
        IncidentSeverity severity,
        @NotBlank(message = "故障现象不能为空")
        @Size(max = 1000, message = "故障现象不能超过 1000 个字符")
        String symptom
) {
}
