package dev.zen.story2script.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zen.story2script.api.dto.AssistantChatMessage;
import dev.zen.story2script.api.dto.AssistantChatRequest;
import dev.zen.story2script.api.dto.AssistantChatResponse;
import dev.zen.story2script.api.dto.AssistantRefineInputRequest;
import dev.zen.story2script.api.dto.AssistantRefineInputResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputAssistantServiceTests {

    private final InputAssistantService service = new InputAssistantService((ChatClient) null, new ObjectMapper());

    @Test
    void refinesShortInputWithoutChangingCoreMeaning() {
        AssistantRefineInputResponse response = service.refine(new AssistantRefineInputRequest(
                "一个女孩回到故乡调查父亲失踪",
                List.of("悬疑", "电影感"),
                "story_to_script_home"
        ));

        assertThat(response.enhancedInput())
                .contains("【用户硬约束】")
                .contains("一个女孩回到故乡调查父亲失踪")
                .contains("【风格偏好】悬疑、电影感");
        assertThat(response.styleHints()).containsExactly("悬疑", "电影感");
        assertThat(response.formatHints())
                .containsEntry("contentType", "小说转脚本")
                .containsEntry("tone", "悬疑、电影感");
        assertThat(response.suggestions()).contains("可以补充主要角色", "可以指定章节数量");
    }

    @Test
    void removesDuplicateBlankStylesAndProvidesToneFallback() {
        AssistantRefineInputResponse response = service.refine(new AssistantRefineInputRequest(
                "标题《雾镇来信》。主角林夏。第一章，她收到旧信。",
                List.of("治愈", " ", "治愈"),
                "story_to_script_home"
        ));

        assertThat(response.styleHints()).containsExactly("治愈");
        assertThat(response.formatHints()).containsEntry("tone", "治愈");
        assertThat(response.suggestions()).doesNotContain("可以补充作品标题", "可以补充主要角色");
    }

    @Test
    void chatFormatCapabilityFocusesOnStructure() {
        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "format",
                "",
                List.of(new AssistantChatMessage("user", "女孩回乡调查父亲失踪")),
                List.of("悬疑"),
                "story_to_script_home"
        ));

        assertThat(response.assistantMessage()).contains("目标", "用户硬约束");
        assertThat(response.enhancedInput())
                .contains("【整理后的素材要求】")
                .contains("【待补充项】");
        assertThat(response.suggestions()).contains("可以补充作品标题", "可以补充主要角色");
    }

    @Test
    void chatStyleCapabilityFocusesOnToneAndVisualDirection() {
        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "style",
                "",
                List.of(new AssistantChatMessage("user", "女孩回乡调查父亲失踪")),
                List.of(),
                "story_to_script_home"
        ));

        assertThat(response.assistantMessage()).contains("风格建议");
        assertThat(response.enhancedInput())
                .contains("【风格偏好/软建议】")
                .contains("【风格执行方向】")
                .contains("悬疑");
        assertThat(response.suggestions()).contains("可以补充节奏偏好，例如紧凑或舒缓");
    }
}
