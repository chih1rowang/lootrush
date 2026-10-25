package com.lootrush.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理器：所有接口的报错在这里翻译成统一的 {code,msg,data} */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务规则不满足（如用户名已占用） */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> biz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数校验不通过（如密码少于6位），把第一条原因返回给前端 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> invalidArgs(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数不合法"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.error(400, msg);
    }

    /** 兜底：未知异常只给用户一句话，完整堆栈进日志（不泄露内部细节） */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> unknown(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(500, "服务器开小差了，请稍后再试");
    }
}
