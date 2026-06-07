package dev.zen.story2script.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 首页输入助手的轻量整理结果。
 */
public record AssistantRefineInputResponse(
        String enhancedInput,
        List<String> styleHints,
        Map<String, String> formatHints,
        List<String> suggestions
) {
}
