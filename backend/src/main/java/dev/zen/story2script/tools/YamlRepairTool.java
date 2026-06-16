package dev.zen.story2script.tools;

import dev.zen.story2script.schema.ScreenplayYamlSchema;
import dev.zen.story2script.schema.YamlSchemaValidationError;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Repairs YAML syntax and screenplay schema formatting issues with an LLM.
 */
@Component
public class YamlRepairTool {

    private final ObjectProvider<ToolLlmClient> llmClientProvider;

    public YamlRepairTool(ToolLlmClient llmClient) {
        this(new StaticToolLlmClientProvider(llmClient));
    }

    @Autowired
    public YamlRepairTool(ObjectProvider<ToolLlmClient> llmClientProvider) {
        this.llmClientProvider = llmClientProvider;
    }

    @Tool(description = "Repair screenplay YAML syntax or schema formatting errors.")
    public YamlRepairOutput repair(YamlRepairInput input) {
        return repair(input, List.of());
    }

    public YamlRepairOutput repair(YamlRepairInput input, List<Advisor> advisors) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.yaml(), "yaml");

        return new YamlRepairOutput(llmClient().generate(systemPrompt(), userPrompt(input), advisors));
    }

    private ToolLlmClient llmClient() {
        return llmClientProvider.getObject();
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

                Additional repair guidance:
                %s

                YAML to repair:
                %s
                """.formatted(formatErrors(input.errors()), ToolInputs.nullToEmpty(input.guidance()), input.yaml());
    }

    private String formatErrors(List<YamlSchemaValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
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

    public record YamlRepairInput(String yaml, List<YamlSchemaValidationError> errors, String guidance) {
        public YamlRepairInput(String yaml, List<YamlSchemaValidationError> errors) {
            this(yaml, errors, "");
        }

        public YamlRepairInput {
            errors = List.copyOf(errors == null ? List.of() : errors);
            guidance = guidance == null ? "" : guidance;
        }
    }

    public record YamlRepairOutput(String yaml) {
    }
}
