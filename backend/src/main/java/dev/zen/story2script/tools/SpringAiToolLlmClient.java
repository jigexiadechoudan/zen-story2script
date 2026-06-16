package dev.zen.story2script.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * Real LLM adapter backed by Spring AI ChatClient.
 */
public class SpringAiToolLlmClient implements ToolLlmClient {

    private final ChatClient chatClient;

    public SpringAiToolLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, List.of());
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, List<Advisor> advisors) {
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(advisors == null ? List.of() : advisors)
                .call()
                .content();
        return content == null ? "" : content.trim();
    }
}
