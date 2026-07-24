package com.opsmind.backend.diagnosis.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis 固定时间窗限制诊断任务创建频率。
 *
 * <p>限流用于保护高成本 AI 调用；Redis 故障时采用失败开放策略，避免缓存故障
 * 反向导致核心诊断入口完全不可用。
 */
@Service
public class DiagnosisRequestRateLimiter {

    /** Redis 不可用时记录 fail-open 预警。 */
    private static final Logger log = LoggerFactory.getLogger(DiagnosisRequestRateLimiter.class);
    /** 固定窗口长度。 */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** 保存每个客户端当前窗口计数。 */
    private final StringRedisTemplate redisTemplate;
    /** 可通过环境变量覆盖的每分钟请求上限。 */
    private final int requestsPerMinute;

    /** 由 Spring 注入 Redis，并读取限流配置。 */
    public DiagnosisRequestRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${opsmind.diagnosis.rate-limit-per-minute:10}") int requestsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = requestsPerMinute;
    }

    /** 超出每分钟额度时抛出可由全局异常处理器转换的业务异常。 */
    public void check(String clientKey) {
        String normalizedClient = clientKey == null || clientKey.isBlank()
                ? "unknown"
                : clientKey.replaceAll("[^a-zA-Z0-9:._-]", "_");
        String key = "opsmind:diagnosis:rate:" + normalizedClient;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW);
            }
            if (count != null && count > requestsPerMinute) {
                throw new DiagnosisRateLimitExceededException(
                        "诊断请求过于频繁，请稍后再试"
                );
            }
        } catch (DiagnosisRateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Redis 限流不可用，本次诊断请求继续执行: client={}", normalizedClient, exception);
        }
    }
}
