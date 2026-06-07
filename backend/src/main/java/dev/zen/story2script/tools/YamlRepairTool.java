package dev.zen.story2script.tools;

import dev.zen.story2script.schema.ScreenplayYamlSchema;
import dev.zen.story2script.schema.YamlSchemaValidationError;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * YAML 修复工具，使用 LLM 修复 YAML 语法或剧本 schema 格式问题。
 *
 * <p>这个类会把校验器返回的错误列表作为修复指导，但自身不重新执行校验。
 * 重试次数、再次校验和流程编排都由调用方或 Agent 决定。</p>
 */
@Component
public class YamlRepairTool {

    private final ToolLlmClient llmClient;

    public YamlRepairTool(ToolLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 修复一段 YAML，并要求模型只返回修复后的 YAML 文本。
     */
    @Tool(description = "Repair screenplay YAML syntax or schema formatting errors.")
    public YamlRepairOutput repair(YamlRepairInput input) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.yaml(), "yaml");

        return new YamlRepairOutput(llmClient.generate(systemPrompt(), userPrompt(input)));
    }

    private String systemPrompt() {
        return """
                You are a YAML repair tool.
                Repair only YAML syntax and screenplay schema formatting issues.
                Preserve story content unless a listed validation error requires normalization.
                The required schema_version is "%s".
                Top-level fields must be exactly: %s.
                Scene scene_type values must be one of: %s.
                Beat type values must be one of: %s.
                Every scene must include at least one action beat and one dialogue beat.
                Every beat must include content. Dialogue beats must include speaker.
                Preserve or add concrete playable action and speakable dialogue when validation errors require it.
                Return only the repaired YAML document.
                Do not return Markdown code fences, commentary, or any text outside the YAML document.
                """.formatted(
                ScreenplayYamlSchema.VERSION,
                ScreenplayYamlSchema.TOP_LEVEL_FIELDS,
                ScreenplayYamlSchema.SCENE_TYPES,
                ScreenplayYamlSchema.BEAT_TYPES
        );
    }

    private String userPrompt(YamlRepairInput input) {
        return """
                Validation errors:
                %s

                YAML to repair:
                %s
                """.formatted(formatErrors(input.errors()), input.yaml());
    }

    private String formatErrors(List<YamlSchemaValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            // 没有结构化错误时，仍允许做纯 YAML 语法/格式修复。
            return "No structured validation errors were provided. Repair obvious YAML syntax and formatting issues only.";
        }

        StringBuilder builder = new StringBuilder();
        for (YamlSchemaValidationError error : errors) {
            builder.append("- path: ").append(error.path())
                    .append(", code: ").append(error.code())
                    .append(", message: ").append(error.message())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    public record YamlRepairInput(String yaml, List<YamlSchemaValidationError> errors) {
        /**
         * errors 为 null 表示没有结构化错误，不表示输入对象非法。
         */
        public YamlRepairInput {
            errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }

    /**
     * 输出：模型返回的原始修复后 YAML 文本。
     */
    public record YamlRepairOutput(String yaml) {
    }
}
