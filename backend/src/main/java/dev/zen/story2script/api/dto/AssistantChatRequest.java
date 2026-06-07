package dev.zen.story2script.api.dto;

import java.util.List;

public record AssistantChatRequest(
        String capability,
        String homeInput,
        List<AssistantChatMessage> messages,
        List<String> selectedStyles,
        String target
) {
}
