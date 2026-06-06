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
        String conversionMode
) {

    public String normalizedConversionMode() {
        if (conversionMode == null || conversionMode.isBlank()) {
            return "fast";
        }
        String normalized = conversionMode.trim().toLowerCase();
        if ("react".equals(normalized) || "full".equals(normalized)) {
            return "react";
        }
        return "fast";
    }
}
