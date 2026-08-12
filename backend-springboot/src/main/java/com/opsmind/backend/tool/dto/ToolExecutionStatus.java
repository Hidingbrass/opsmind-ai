package com.opsmind.backend.tool.dto;

/** 对外工具响应的两种执行结果。 */
public enum ToolExecutionStatus {
    /** 工具已正常执行，data 中包含结果（包括合法的空列表）。 */
    SUCCESS,
    /** 参数校验、白名单或具体工具执行失败。 */
    FAILED
}
