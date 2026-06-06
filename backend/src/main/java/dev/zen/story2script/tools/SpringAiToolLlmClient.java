package dev.zen.story2script.tools;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 真实 LLM 适配器，使用 Spring AI ChatClient 调用模型。
 *
 * <p>只有存在 {@link ChatModel} Bean 时才创建这个实现。这样本地或测试环境
 * 即使没有配置模型，也能启动 Spring 上下文。</p>
 */
@Component
@ConditionalOnBean(ChatModel.class)
class SpringAiToolLlmClient implements ToolLlmClient {

    private final ChatClient chatClient;

    SpringAiToolLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        // 工具层只负责发送 Prompt，不编排 Agent 步骤。
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        return content == null ? "" : content.trim();
    }
}
