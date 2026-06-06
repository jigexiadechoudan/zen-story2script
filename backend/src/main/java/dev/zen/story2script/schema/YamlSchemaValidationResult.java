package dev.zen.story2script.schema;

import java.util.List;
import java.util.Map;

/**
 * 剧本 YAML 解析和校验结果。
 *
 * <p>如果 YAML 已经解析成功但结构校验失败，{@code document} 仍会保留
 * 解析后的顶层 Map，方便调用方检查或修复草稿。</p>
 */
public record YamlSchemaValidationResult(
        boolean valid,
        Map<String, Object> document,
        List<YamlSchemaValidationError> errors
) {

    public static YamlSchemaValidationResult valid(Map<String, Object> document) {
        return new YamlSchemaValidationResult(true, document, List.of());
    }

    public static YamlSchemaValidationResult invalid(
            Map<String, Object> document,
            List<YamlSchemaValidationError> errors
    ) {
        return new YamlSchemaValidationResult(false, document, List.copyOf(errors));
    }
}
