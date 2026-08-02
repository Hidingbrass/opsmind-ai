package com.opsmind.backend.tool.model;

/** 工具调用审计表的持久化结果，后续可独立扩展 REJECTED 或 TIMEOUT。 */
public enum ToolCallStatus {
    /** 工具正常返回结果。 */
    SUCCESS,
    /** 调用在校验或执行阶段失败。 */
    FAILED
}
