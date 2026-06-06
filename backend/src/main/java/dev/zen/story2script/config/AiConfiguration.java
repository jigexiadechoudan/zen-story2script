package dev.zen.story2script.config;

import dev.zen.story2script.tools.SpringAiToolLlmClient;
import dev.zen.story2script.tools.ToolLlmClient;
import dev.zen.story2script.tools.UnavailableToolLlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring AI chat-client wiring.
 *
 * <p>The default {@code dev} profile deliberately does not create an OpenAI chat model. That lets tests and local
 * development start without an API key. Profiles that enable {@link ChatModel} get a {@link ChatClient} with any
 * discovered tool callbacks attached.
 */
@Configuration(proxyBeanMethods = false)
public class AiConfiguration {

    /**
     * Creates the application-level chat client only when a model provider is active.
     *
     * <p>{@code application-dev.yml} disables model auto-configuration, so this bean is absent in dev without an API
     * key. In local/prod, Spring AI auto-configures {@link ChatModel} from the OpenAI environment-backed properties.
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(ChatClient.class)
    ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean
    @ConditionalOnMissingBean(ToolLlmClient.class)
    @Profile("!dev")
    ToolLlmClient toolLlmClient(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        ChatClient.Builder chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
        if (chatModel != null && chatClientBuilder != null) {
            return new SpringAiToolLlmClient(chatClientBuilder);
        }
        return new UnavailableToolLlmClient();
    }
}
