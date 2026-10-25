package com.lootrush.common;

/** 业务异常：带上业务错误码，由全局异常处理器统一翻译成响应 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException of(int code, String msg) {
        return new BizException(code, msg);
    }

    public int getCode() {
        return code;
    }
}
