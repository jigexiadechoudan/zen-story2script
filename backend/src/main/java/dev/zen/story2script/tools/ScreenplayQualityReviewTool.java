package dev.zen.story2script.tools;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.schema.YamlSchemaValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight screenplay craft review focused on performability and scene density.
 */
@Component
public class ScreenplayQualityReviewTool {

    private final YamlSchemaValidator validator;

    public ScreenplayQualityReviewTool() {
        this(new YamlSchemaValidator());
    }

    ScreenplayQualityReviewTool(YamlSchemaValidator validator) {
        this.validator = validator;
    }

    @Tool(name = "screenplay_quality_review", description = "Review whether screenplay YAML is performable and scene-level.")
    public ScreenplayQualityReviewOutput review(ScreenplayQualityReviewInput input) {
        input = ToolInputs.requireInput(input);
        String yaml = ToolInputs.requireText(input.yaml(), "yaml");
        YamlSchemaValidationResult validation = validator.validate(yaml);
        if (!validation.valid()) {
            List<String> findings = validation.errors().stream()
                    .map(error -> error.path() + " " + error.code() + ": " + error.message())
                    .toList();
            return new ScreenplayQualityReviewOutput(false, "Schema validation issues must be fixed first.", findings);
        }

        List<String> findings = new ArrayList<>();
        List<?> scenes = listField(validation.document(), "scenes");
        if (scenes.size() < 3) {
            findings.add("Draft has fewer than 3 scenes; the adaptation may be too compressed.");
        }

        int scenesWithSparseBeats = 0;
        int scenesMissingConflictWords = 0;
        for (Object item : scenes) {
            if (!(item instanceof Map<?, ?> scene)) {
                continue;
            }
            List<?> beats = listField(scene, "beats");
            if (beats.size() < 3) {
                scenesWithSparseBeats++;
            }
            String dramaticPurpose = textField(scene, "dramatic_purpose");
            String summary = textField(scene, "summary");
            if (!containsConflictSignal(dramaticPurpose + " " + summary)) {
                scenesMissingConflictWords++;
            }
        }

        if (scenesWithSparseBeats > 0) {
            findings.add("%d scene(s) have fewer than 3 beats; add staged action or dialogue.".formatted(scenesWithSparseBeats));
        }
        if (scenesMissingConflictWords > 0) {
            findings.add("%d scene(s) may not state clear conflict or pressure.".formatted(scenesMissingConflictWords));
        }
        if (findings.isEmpty()) {
            findings.add("No obvious performability issue found.");
        }
        return new ScreenplayQualityReviewOutput(findings.size() == 1 && findings.getFirst().startsWith("No obvious"),
                "Reviewed %d scene(s) for playable beats and conflict.".formatted(scenes.size()),
                findings);
    }

    private List<?> listField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value instanceof List<?> list ? list : List.of();
    }

    private String textField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean containsConflictSignal(String value) {
        String text = value.toLowerCase();
        return text.contains("conflict")
                || text.contains("pressure")
                || text.contains("choice")
                || text.contains("confront")
                || text.contains("threat")
                || text.contains("冲突")
                || text.contains("压力")
                || text.contains("对峙")
                || text.contains("选择")
                || text.contains("危机");
    }

    public record ScreenplayQualityReviewInput(String title, String yaml) {
    }

    public record ScreenplayQualityReviewOutput(boolean pass, String summary, List<String> findings) {
        public ScreenplayQualityReviewOutput {
            findings = List.copyOf(findings == null ? List.of() : findings);
        }
    }
}
