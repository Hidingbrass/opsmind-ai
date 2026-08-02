package com.opsmind.backend.diagnosis.service;

/** 诊断创建请求超过 Redis 时间窗额度时抛出的业务异常。 */
public class DiagnosisRateLimitExceededException extends RuntimeException {

    /** @param message 可由全局异常处理器返回给前端的限流提示 */
    public DiagnosisRateLimitExceededException(String message) {
        super(message);
    }
}
