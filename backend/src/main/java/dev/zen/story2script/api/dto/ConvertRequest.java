package dev.zen.story2script.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 小说转剧本请求体。
 */
public record ConvertRequest(
        @NotBlank String title,
        @NotBlank String sourceText,
        @NotBlank String targetFormat,
        String styleHint,
        String conversionMode,
        String language
) {

    public ConvertRequest(
            String title,
            String sourceText,
            String targetFormat,
            String styleHint,
            String conversionMode
    ) {
        this(title, sourceText, targetFormat, styleHint, conversionMode, null);
    }

    public String normalizedConversionMode() {
        if (conversionMode == null || conversionMode.isBlank()) {
            return "fast";
        }
        String normalized = conversionMode.trim().toLowerCase();
        if ("fast".equals(normalized)) {
            return "fast";
        }
        return "react";
    }

    public String normalizedLanguage() {
        if (language == null || language.isBlank()) {
            return "zh-CN";
        }
        String normalized = language.trim();
        if (normalized.equalsIgnoreCase("en") || normalized.toLowerCase().startsWith("en-")) {
            return "en-US";
        }
        if (normalized.equalsIgnoreCase("zh") || normalized.toLowerCase().startsWith("zh-")) {
            return "zh-CN";
        }
        return normalized;
    }
}
