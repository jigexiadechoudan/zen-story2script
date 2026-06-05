package dev.zen.story2script.api.error;

/**
 * 统一错误响应。
 *
 * <p>所有 API 错误都返回 code 和 message，避免不同异常路径产生不同 JSON 结构。
 */
public record ErrorResponse(
        // 稳定、机器可读的错误码，前端可基于它做分支处理。
        String code,

        // 面向调用方的简短错误描述，不包含堆栈和服务端敏感细节。
        String message
) {
}
