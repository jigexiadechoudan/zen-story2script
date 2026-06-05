package dev.zen.story2script.api.error;

import org.springframework.http.HttpStatus;

// API 层本地异常，用于把业务失败映射为统一错误响应结构。
public class ApiException extends RuntimeException {

    // HTTP 状态码由抛出异常的位置决定，避免在全局异常处理器里猜测业务语义。
    private final HttpStatus status;

    // 面向前端和调用方的稳定错误码，不直接暴露 Java 异常类名。
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
