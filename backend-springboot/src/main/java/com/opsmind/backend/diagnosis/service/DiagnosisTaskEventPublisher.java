package com.opsmind.backend.diagnosis.service;

import java.util.Map;
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

    /** taskId 到当前 SSE 连接的线程安全映射。 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 为指定任务创建长连接，并注册完成、超时和错误清理回调。
     *
     * @param taskId 订阅的诊断任务 id
     * @return 要由 Controller 返回的 SSE 连接
     */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        // 按 taskId 保存连接，让异步执行线程能找到正在订阅的前端。
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> emitters.remove(taskId, emitter));
        emitter.onError(error -> emitters.remove(taskId, emitter));

        return emitter;
    }

    /**
     * 向指定任务的当前订阅者发送事件；没有订阅者时直接返回。
     *
     * @param taskId 事件所属任务
     * @param event 要序列化为 SSE data 的事件
     */
    public void publish(String taskId, DiagnosisTaskEvent event) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(event.stage())
                    .data(event));

            if (isTerminal(event.status())) {
                emitters.remove(taskId, emitter);
                emitter.complete();
            }
        } catch (Exception ex) {
            // SSE 只是通知通道，连接失效时只清理连接，不把异常传回诊断流程。
            emitters.remove(taskId, emitter);
            emitter.completeWithError(ex);
        }
    }

    /** @return SUCCESS 或 FAILED 时返回 true，终态推送后应关闭连接 */
    private boolean isTerminal(DiagnosisTaskStatus status) {
        return status == DiagnosisTaskStatus.SUCCESS
                || status == DiagnosisTaskStatus.FAILED;
    }
}
