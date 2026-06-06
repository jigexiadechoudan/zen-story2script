package dev.zen.story2script.agent;

import org.springframework.lang.Nullable;

/**
 * Input context for one novel-to-screenplay agent run.
 */
public record AgentContext(
        String title,
        String sourceText,
        @Nullable String originalAuthor,
        @Nullable String language,
        String targetFormat,
        @Nullable String targetDuration,
        @Nullable String styleHint
) {

    public AgentContext {
        title = requireText(title, "title");
        sourceText = requireText(sourceText, "sourceText");
        targetFormat = requireText(targetFormat, "targetFormat");
    }

    public static AgentContext of(String title, String sourceText, String targetFormat, @Nullable String styleHint) {
        return new AgentContext(title, sourceText, null, null, targetFormat, null, styleHint);
    }

    private static String requireText(@Nullable String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
