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
        @Nullable String styleHint,
        String conversionMode
) {

    public AgentContext {
        title = requireText(title, "title");
        sourceText = requireText(sourceText, "sourceText");
        targetFormat = requireText(targetFormat, "targetFormat");
        conversionMode = normalizeMode(conversionMode);
    }

    public static AgentContext of(String title, String sourceText, String targetFormat, @Nullable String styleHint) {
        return new AgentContext(title, sourceText, null, null, targetFormat, null, styleHint, "react");
    }

    public static AgentContext of(
            String title,
            String sourceText,
            String targetFormat,
            @Nullable String styleHint,
            String conversionMode
    ) {
        return new AgentContext(title, sourceText, null, null, targetFormat, null, styleHint, conversionMode);
    }

    public static AgentContext of(
            String title,
            String sourceText,
            String targetFormat,
            @Nullable String styleHint,
            String conversionMode,
            @Nullable String language
    ) {
        return new AgentContext(title, sourceText, null, language, targetFormat, null, styleHint, conversionMode);
    }

    public boolean fastMode() {
        return "fast".equals(conversionMode);
    }

    private static String requireText(@Nullable String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeMode(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "react";
        }
        String normalized = value.trim().toLowerCase();
        if ("fast".equals(normalized)) {
            return "fast";
        }
        return "react";
    }
}
