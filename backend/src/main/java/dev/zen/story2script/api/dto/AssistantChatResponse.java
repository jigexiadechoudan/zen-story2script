package dev.zen.story2script.api.dto;

import java.util.List;
import java.util.Map;

public record AssistantChatResponse(
        String assistantMessage,
        String enhancedInput,
        List<String> styleHints,
        Map<String, String> formatHints,
        List<String> suggestions
) {
}
