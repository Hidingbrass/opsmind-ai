package com.opsmind.backend.diagnosis.service;

/** 同一故障正在并发创建任务但尚未得到可复用 taskId 时的短暂冲突。 */
public class DiagnosisTaskConflictException extends RuntimeException {

    /** @param message 提示调用方稍后重试的并发冲突消息 */
    public DiagnosisTaskConflictException(String message) {
        super(message);
    }
}
