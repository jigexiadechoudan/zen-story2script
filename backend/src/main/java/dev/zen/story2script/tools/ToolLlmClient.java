package dev.zen.story2script.tools;

import org.springframework.ai.chat.client.advisor.api.Advisor;

import java.util.List;

/**
 * Abstraction for one LLM generation call used by conversion tools.
 */
public interface ToolLlmClient {

    String generate(String systemPrompt, String userPrompt);

    default String generate(String systemPrompt, String userPrompt, List<Advisor> advisors) {
        return generate(systemPrompt, userPrompt);
    }

    /**
     * Whether this client is the dev-only demonstration fallback instead of a real model adapter.
     */
    default boolean devFallback() {
        return false;
    }
}
