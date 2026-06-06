package dev.zen.story2script.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 分场规划工具，使用 LLM 把故事分析结果转换成场景级计划。
 *
 * <p>这个类只做一次模型调用并返回 JSON 文本，不实现 Agent 循环、重试或后续工具调度。</p>
 */
@Component
public class ScenePlanningTool {

    private final ToolLlmClient llmClient;

    public ScenePlanningTool(ToolLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 根据故事分析 JSON 生成分场计划。
     *
     * <p>返回值仍是中间产物，调用方可以检查、重试，或继续传给 YAML 草稿生成工具。</p>
     */
    @Tool(description = "Generate a scene plan from story analysis.")
    public ScenePlanningOutput plan(ScenePlanningInput input) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.title(), "title");
        ToolInputs.requireText(input.analysisJson(), "analysisJson");

        return new ScenePlanningOutput(llmClient.generate(systemPrompt(), userPrompt(input)));
    }

    private String systemPrompt() {
        return """
                You are a screenplay scene planning tool.
                Return only a valid JSON object with this shape:
                {
                  "scenes": [{
                    "sceneId": "S001",
                    "sceneType": "INT|EXT|INT/EXT",
                    "location": "",
                    "timeOfDay": "",
                    "characters": [""],
                    "summary": "",
                    "dramaticPurpose": "",
                    "sourceChapters": [""]
                  }],
                  "adaptationNotes": [""]
                }
                Do not return Markdown code fences, commentary, or any text outside the JSON object.
                """;
    }

    private String userPrompt(ScenePlanningInput input) {
        return """
                Title: %s
                Target format: %s
                Target duration: %s
                Story analysis JSON:
                %s
                """.formatted(
                input.title(),
                ToolInputs.nullToEmpty(input.targetFormat()),
                ToolInputs.nullToEmpty(input.targetDuration()),
                input.analysisJson()
        );
    }

    public record ScenePlanningInput(
            String title,
            String analysisJson,
            String targetFormat,
            String targetDuration
    ) {
    }

    /**
     * 输出：模型返回的原始 JSON，包含 scenes 和 adaptationNotes。
     */
    public record ScenePlanningOutput(String scenePlanJson) {
    }
}
