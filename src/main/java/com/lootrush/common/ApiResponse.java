package com.lootrush.common;

/** 统一响应体：所有接口都返回 {code, msg, data}，code=0 表示成功 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static ApiResponse<Void> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
