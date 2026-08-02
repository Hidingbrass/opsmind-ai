package com.opsmind.backend.diagnosis.service;

import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 中的诊断任务加速层。
 *
 * <p>MySQL 始终是可靠来源；Redis 只负责状态快照、重复任务锁和成功结果短期复用。
 * Redis 不可用时所有方法都会安全降级，不阻断诊断主链路。
 */
@Service
public class DiagnosisTaskCacheService {

    /** Redis 旁路异常只告警，不中断 MySQL 主链路。 */
    private static final Logger log = LoggerFactory.getLogger(DiagnosisTaskCacheService.class);
    /** 单个任务状态快照保留时间。 */
    private static final Duration TASK_TTL = Duration.ofHours(1);
    /** 同一故障可复用成功任务的时间窗口。 */
    private static final Duration REUSE_TTL = Duration.ofMinutes(10);
    /** 防止创建线程崩溃后永久占锁的最大锁时间。 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    /** 以字符串形式读写任务 JSON、计数器和锁。 */
    private final StringRedisTemplate redisTemplate;
    /** 在任务 DTO 与缓存 JSON 之间转换。 */
    private final ObjectMapper objectMapper;

    /** 由 Spring 注入 Redis 和 JSON 基础能力。 */
    public DiagnosisTaskCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 缓存任务状态快照，供轮询和页面刷新快速读取。 */
    public void putTask(DiagnosisTaskResponse task) {
        try {
            redisTemplate.opsForValue().set(
                    taskKey(task.id()),
                    objectMapper.writeValueAsString(task),
                    TASK_TTL
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("缓存诊断任务状态失败，继续使用 MySQL: taskId={}", task.id(), exception);
        }
    }

    /** 读取任务快照；Redis 缺失或内容损坏时由调用者回源 MySQL。 */
    public Optional<DiagnosisTaskResponse> getTask(String taskId) {
        try {
            String json = redisTemplate.opsForValue().get(taskKey(taskId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, DiagnosisTaskResponse.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取诊断任务缓存失败，回源 MySQL: taskId={}", taskId, exception);
            return Optional.empty();
        }
    }

    /** 获取同一故障最近可复用的进行中或成功任务 id。 */
    public Optional<String> getReusableTaskId(String incidentId) {
        try {
            return Optional.ofNullable(
                    redisTemplate.opsForValue().get(reuseKey(incidentId))
            );
        } catch (DataAccessException exception) {
            log.warn("读取重复诊断缓存失败: incidentId={}", incidentId, exception);
            return Optional.empty();
        }
    }

    /** 新建任务前申请短期分布式锁，防止并发创建相同故障的诊断。 */
    public boolean tryAcquireIncidentLock(String incidentId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey(incidentId),
                    "locked",
                    LOCK_TTL
            );
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException exception) {
            log.warn("Redis 去重锁不可用，允许请求继续由数据库兜底: incidentId={}", incidentId, exception);
            return true;
        }
    }

    /** 新任务创建后立即登记，使后续相同请求可以复用该任务。 */
    public void rememberTask(String incidentId, String taskId) {
        try {
            redisTemplate.opsForValue().set(reuseKey(incidentId), taskId, REUSE_TTL);
        } catch (DataAccessException exception) {
            log.warn("保存重复诊断索引失败: incidentId={}", incidentId, exception);
        }
    }

    /** 任务结束后释放进行中锁；失败任务同时取消复用索引。 */
    public void finishTask(String incidentId, boolean successful) {
        try {
            redisTemplate.delete(lockKey(incidentId));
            if (!successful) {
                redisTemplate.delete(reuseKey(incidentId));
            }
        } catch (DataAccessException exception) {
            log.warn("清理诊断任务 Redis 状态失败: incidentId={}", incidentId, exception);
        }
    }

    /** 构造单个任务状态快照的 Redis key。 */
    private String taskKey(String taskId) {
        return "opsmind:diagnosis:task:" + taskId;
    }

    /** 构造故障到可复用任务 id 的 Redis key。 */
    private String reuseKey(String incidentId) {
        return "opsmind:diagnosis:reuse:" + incidentId;
    }

    /** 构造同一故障创建任务时使用的分布式锁 key。 */
    private String lockKey(String incidentId) {
        return "opsmind:diagnosis:lock:" + incidentId;
    }
}
