package com.opsmind.backend.diagnosis.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接管理器和任务事件发布器。
 *
 * <p>Controller 线程调用 subscribe 建立连接，异步执行线程调用 publish 发送消息，
 * 因此需要使用线程安全的 ConcurrentHashMap。
 */
@Service
public class DiagnosisTaskEventPublisher {
    /** 单条 SSE 连接最长保持 5 分钟，防止无限占用服务器资源。 */
    private static final long SSE_TIMEOUT_MILLIS = 5 * 60 * 1000L;

    /** taskId 到全部 SSE 连接的线程安全映射，支持刷新和多标签页同时订阅。 */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 为指定任务创建长连接，并注册完成、超时和错误清理回调。
     *
     * @param taskId 订阅的诊断任务 id
     * @return 要由 Controller 返回的 SSE 连接
     */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.compute(taskId, (ignored, subscribers) -> {
            Set<SseEmitter> updatedSubscribers = subscribers == null
                    ? ConcurrentHashMap.newKeySet()
                    : subscribers;
            updatedSubscribers.add(emitter);
            return updatedSubscribers;
        });

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(taskId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeEmitter(taskId, emitter));

        return emitter;
    }

    /**
     * 向指定任务的全部当前订阅者发送事件；没有订阅者时直接返回。
     *
     * @param taskId 事件所属任务
     * @param event 要序列化为 SSE data 的事件
     */
    public void publish(String taskId, DiagnosisTaskEvent event) {
        Set<SseEmitter> subscribers = emitters.get(taskId);
        if (subscribers == null) {
            return;
        }

        for (SseEmitter emitter : Set.copyOf(subscribers)) {
            sendToSubscriber(taskId, emitter, event);
        }
    }

    /**
     * 只向刚建立的连接发送当前数据库快照，避免两个并发订阅互相重复广播终态。
     */
    void sendSnapshot(
            String taskId,
            SseEmitter emitter,
            DiagnosisTaskEvent event
    ) {
        sendToSubscriber(taskId, emitter, event);
    }

    /** 仅供同包测试和运行诊断确认指定任务的当前订阅数。 */
    int subscriberCount(String taskId) {
        Set<SseEmitter> subscribers = emitters.get(taskId);
        return subscribers == null ? 0 : subscribers.size();
    }

    /** 只移除指定连接，并在最后一条连接关闭后清理 taskId。 */
    private void removeEmitter(String taskId, SseEmitter emitter) {
        emitters.computeIfPresent(taskId, (ignored, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    /** 单连接发送与清理必须幂等，重连竞态不能反向打断 Controller 请求。 */
    private void sendToSubscriber(
            String taskId,
            SseEmitter emitter,
            DiagnosisTaskEvent event
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.stage())
                    .data(event));
            if (isTerminal(event.status())) {
                removeEmitter(taskId, emitter);
                emitter.complete();
            }
        } catch (Exception exception) {
            removeEmitter(taskId, emitter);
            try {
                emitter.completeWithError(exception);
            } catch (IllegalStateException ignored) {
                // 另一个并发终态发送可能已完成该连接，重复清理不再向外抛出。
            }
        }
    }

    /** @return SUCCESS 或 FAILED 时返回 true，终态推送后应关闭连接 */
    private boolean isTerminal(DiagnosisTaskStatus status) {
        return status == DiagnosisTaskStatus.SUCCESS
                || status == DiagnosisTaskStatus.FAILED;
    }
}
