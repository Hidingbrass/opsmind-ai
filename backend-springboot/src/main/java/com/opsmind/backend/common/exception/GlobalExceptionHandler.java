package com.opsmind.backend.common.exception;

import com.opsmind.backend.common.web.Result;
import com.opsmind.backend.diagnosis.service.DiagnosisRateLimitExceededException;
import com.opsmind.backend.diagnosis.service.DiagnosisTaskConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Controller 调用链抛出的异常转换成统一 {@link Result} JSON 结构。
 *
 * <p>业务代码只需抛出有意义的异常，不需要在每个 Controller 重复组装错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 将诊断限流转换为标准 HTTP 429，前端可提示用户稍后重试。 */
    @ExceptionHandler(DiagnosisRateLimitExceededException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(
            DiagnosisRateLimitExceededException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.failure(429, ex.getMessage()));
    }

    /** 并发去重窗口返回 HTTP 409，调用方可稍后重试而不是误认为系统故障。 */
    @ExceptionHandler(DiagnosisTaskConflictException.class)
    public ResponseEntity<Result<Void>> handleTaskConflict(
            DiagnosisTaskConflictException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Result.failure(409, ex.getMessage()));
    }

    /**
     * 处理参数错误、资源不存在等可预期的业务拒绝。
     *
     * @param ex Service 抛出的业务参数异常
     * @return HTTP 400 和可直接展示的错误消息
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(400, ex.getMessage()));
    }

    /**
     * 兜底处理未预期异常，对外隐藏堆栈和内部实现细节。
     *
     * @param ex 未被更具体处理器匹配的异常
     * @return HTTP 500 和统一系统错误消息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(500, "系统异常，请稍后重试"));
    }
}
