package dev.zen.story2script.api.dto;

/**
 * 健康检查响应。
 *
 * <p>status 固定返回 ok，保持验收标准和前端探活逻辑简单。
 */
public record HealthResponse(String status) {
}
