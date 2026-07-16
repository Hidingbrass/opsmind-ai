package com.opsmind.backend.common.web;

/**
 * 所有普通 JSON API 的统一响应外层。
 *
 * @param code 业务状态码，0 表示成功
 * @param message 供调用方阅读的结果描述
 * @param data 具体业务数据，失败时通常为 null
 * @param <T> 业务数据类型
 */
public record Result<T>(
        int code,
        String message,
        T data
) {

    /** 全项目统一的成功业务码。 */
    private static final int SUCCESS_CODE = 0;

    /** 全项目统一的成功提示文本。 */
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 包装带业务数据的成功响应。
     *
     * @param data Controller 返回给调用方的数据
     * @return code 为 0 的统一响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 包装不需要返回业务数据的成功响应。
     *
     * @return data 为 null 的成功响应
     */
    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 包装失败响应，由全局异常处理器等边界层调用。
     *
     * @param code 业务错误码
     * @param message 可读错误原因
     * @return data 为 null 的失败响应
     */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
