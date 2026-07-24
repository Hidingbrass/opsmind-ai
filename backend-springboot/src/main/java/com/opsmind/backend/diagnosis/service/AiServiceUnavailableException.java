package com.opsmind.backend.diagnosis.service;

/** Python AI 服务经过重试和熔断保护后仍不可用时抛出的明确降级异常。 */
public class AiServiceUnavailableException extends RuntimeException {

    /** 保留内部原因供日志排查，对外只展示友好的 message。 */
    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
