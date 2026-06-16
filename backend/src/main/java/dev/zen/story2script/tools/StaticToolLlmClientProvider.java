package dev.zen.story2script.tools;

import org.springframework.beans.factory.ObjectProvider;

final class StaticToolLlmClientProvider implements ObjectProvider<ToolLlmClient> {

    private final ToolLlmClient llmClient;

    StaticToolLlmClientProvider(ToolLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public ToolLlmClient getObject(Object... args) {
        return llmClient;
    }

    @Override
    public ToolLlmClient getIfAvailable() {
        return llmClient;
    }

    @Override
    public ToolLlmClient getIfUnique() {
        return llmClient;
    }

    @Override
    public ToolLlmClient getObject() {
        return llmClient;
    }
}
