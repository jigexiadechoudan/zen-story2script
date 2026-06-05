package dev.zen.story2script.api.controller;

import dev.zen.story2script.api.dto.SchemaResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 面向前端发现用的轻量契约端点，不是完整 YAML Schema 校验器。
@RestController
@RequestMapping("/api/schema")
class SchemaController {

    // 初始契约版本。后续 YAML 结构发生不兼容变化时应递增。
    private static final String SCHEMA_VERSION = "0.1.0";

    @GetMapping
    SchemaResponse schema() {
        // 这里描述 HTTP API 的最小输入输出契约，不承诺覆盖完整剧本 YAML 结构。
        return new SchemaResponse(
                SCHEMA_VERSION,
                List.of(
                        new SchemaResponse.Field("title", "string", true),
                        new SchemaResponse.Field("sourceText", "string", true),
                        new SchemaResponse.Field("targetFormat", "string", true),
                        new SchemaResponse.Field("styleHint", "string", false)
                ),
                List.of(
                        "yaml",
                        "schemaVersion",
                        "warnings",
                        "qualityReport",
                        "agentTrace"
                )
        );
    }
}
