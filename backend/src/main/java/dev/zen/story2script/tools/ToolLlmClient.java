package dev.zen.story2script.tools;

/**
 * LLM 调用抽象接口。
 *
 * <p>工具类依赖这个接口，而不是直接依赖具体模型供应商。API key、模型名、
 * base URL 等配置由 Spring 注入到具体实现中，这里不读取敏感配置。</p>
 */
public interface ToolLlmClient {

    /**
     * 执行一次模型生成：systemPrompt 放规则，userPrompt 放本次任务输入。
     */
    String generate(String systemPrompt, String userPrompt);
}
