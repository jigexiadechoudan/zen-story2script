package dev.zen.story2script.tools;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.schema.YamlSchemaValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Tool-facing wrapper around screenplay YAML schema validation.
 */
@Component
public class YamlValidationTool {

    private final YamlSchemaValidator validator;

    public YamlValidationTool() {
        this(new YamlSchemaValidator());
    }

    YamlValidationTool(YamlSchemaValidator validator) {
        this.validator = validator;
    }

    @Tool(description = "Validate screenplay YAML against the required schema and return structured validation errors.")
    public YamlSchemaValidationResult validate(YamlValidationInput input) {
        input = ToolInputs.requireInput(input);
        String yaml = ToolInputs.requireText(input.yaml(), "yaml");
        return validator.validate(yaml);
    }

    public record YamlValidationInput(String yaml) {
    }
}
