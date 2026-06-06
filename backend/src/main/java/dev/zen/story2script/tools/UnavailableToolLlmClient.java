package dev.zen.story2script.tools;

/**
 * 无模型配置时的兜底 LLM 实现。
 *
 * <p>它让不需要 LLM 的测试或 profile 可以正常启动；如果真的调用了 LLM 工具，
 * 会抛出明确错误，提示需要先配置模型。</p>
 */
public class UnavailableToolLlmClient implements ToolLlmClient {

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        throw new IllegalStateException(
                "No Spring AI ChatModel is configured. Configure a model provider before invoking LLM tools."
        );
    }
}
