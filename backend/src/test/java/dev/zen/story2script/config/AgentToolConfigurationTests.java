package dev.zen.story2script.config;

import dev.zen.story2script.rag.RagKnowledgeService;
import dev.zen.story2script.tools.ToolLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ToolLlmClient.class, () -> (systemPrompt, userPrompt) -> "{}")
            .withBean(RagKnowledgeService.class, () -> RagKnowledgeService.DISABLED)
            .withUserConfiguration(AgentToolConfiguration.class);

    @Test
    void registersToolCallbackProviderBean() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ToolCallbackProvider.class)
                .hasBean("agentToolCallbackProvider"));
    }
}
