package com.opsmind.backend.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供最小化存活检查，用于本地调试、容器编排和演示环境确认后端已启动。
 */
@RestController
public class HealthController {

    /**
     * 返回当前后端进程的基础健康信息。
     *
     * @return 服务名、UP 状态和当前时间
     */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "opsmind-backend",
                "status", "UP",
                "time", Instant.now().toString()
        );
    }
}
