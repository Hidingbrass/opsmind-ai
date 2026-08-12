package com.opsmind.backend.observability.model;

import java.time.Instant;

/**
 * 模拟发布平台返回的一条部署记录。
 *
 * @param deployedAt 发布时间
 * @param serviceName 发布服务
 * @param version 应用版本
 * @param commitId 源码提交短 id
 * @param operator 发布人或自动化账号
 * @param status 发布状态
 * @param summary 变更摘要
 */
public record DeploymentRecord(
        Instant deployedAt,
        String serviceName,
        String version,
        String commitId,
        String operator,
        String status,
        String summary
) {
}
