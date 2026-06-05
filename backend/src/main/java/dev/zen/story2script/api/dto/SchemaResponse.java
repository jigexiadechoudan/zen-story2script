package dev.zen.story2script.api.dto;

import java.util.List;

/**
 * /api/schema 的响应体。
 *
 * <p>该结构服务于前端发现和联调，只描述 HTTP 请求/响应字段，不替代 schema 包里的 YAML 校验能力。
 */
public record SchemaResponse(
        // 当前 API/YAML 契约版本。
        String schemaVersion,

        // convert 请求体字段说明。
        List<Field> requestFields,

        // convert 响应体顶层字段说明。
        List<String> responseFields
) {

    /**
     * 请求字段元信息。
     */
    public record Field(
            // 字段名，和 JSON 请求体字段保持一致。
            String name,

            // 字段类型的轻量描述，供前端动态表单或文档展示使用。
            String type,

            // 是否必填；当前只表达 API 层 Bean Validation 的基础要求。
            boolean required
    ) {
    }
}
