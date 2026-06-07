package dev.zen.story2script.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 首页输入助手的轻量整理请求。
 */
public record AssistantRefineInputRequest(
        @NotBlank String rawInput,
        List<String> selectedStyles,
        String target
) {
}
