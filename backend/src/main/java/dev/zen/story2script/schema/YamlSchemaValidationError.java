package dev.zen.story2script.schema;

/**
 * 供 Agent 工具和 YAML 修复逻辑使用的结构化校验错误。
 *
 * @param path 类 JSONPath 的 YAML 定位，例如 {@code $.scenes[0].beats[1].type}
 * @param code 稳定的机器可读错误码
 * @param message 面向日志、UI 或 API 响应的人类可读说明
 */
public record YamlSchemaValidationError(
        String path,
        String code,
        String message
) {
}
