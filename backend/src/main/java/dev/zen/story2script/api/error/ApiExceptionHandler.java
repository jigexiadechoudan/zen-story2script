package dev.zen.story2script.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 统一 API 错误响应，保证客户端始终收到稳定的 { code, message } 结构。
@RestControllerAdvice(basePackages = "dev.zen.story2script.api")
class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        // 业务可预期错误直接使用异常携带的状态码和错误码。
        return ResponseEntity
                .status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        // 只返回第一个字段级校验问题；细粒度 YAML 校验暂不在当前 API 实现范围内。
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse("Request validation failed.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_request", message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleException(Exception exception) {
        // 未分类异常统一收敛为 internal_error，避免把服务端实现细节泄漏给客户端。
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Internal server error."));
    }

    private String formatFieldError(FieldError error) {
        String defaultMessage = error.getDefaultMessage();
        if (defaultMessage == null || defaultMessage.isBlank()) {
            // Bean Validation 没有提供默认文案时，仍然返回可定位到字段的错误信息。
            return error.getField() + " is invalid.";
        }
        return error.getField() + " " + defaultMessage;
    }
}
