package dev.zen.story2script.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 故事分析工具，使用 LLM 从已切分章节中抽取改编所需的信息。
 *
 * <p>这个类只生成中间 JSON：角色、事件、冲突。它不负责重试、不调用下一个工具，
 * 也不控制 Agent 主流程。</p>
 */
@Component
public class StoryAnalysisTool {

    private final ToolLlmClient llmClient;

    public StoryAnalysisTool(ToolLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 从章节文本中抽取角色、事件和冲突。
     *
     * <p>输出保持为模型返回的原始 JSON 字符串，方便单元测试 mock
     * {@link ToolLlmClient}，也方便 Agent 在后续步骤自行校验或修复。</p>
     */
    @Tool(description = "Extract characters, events, and conflicts from parsed chapters.")
    public StoryAnalysisOutput analyze(StoryAnalysisInput input) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.title(), "title");
        if (input.chapters() == null || input.chapters().isEmpty()) {
            throw new IllegalArgumentException("chapters must not be empty");
        }

        return new StoryAnalysisOutput(llmClient.generate(systemPrompt(), userPrompt(input)));
    }

    private String systemPrompt() {
        return """
                You are a story analysis tool for adapting novels into screenplays.
                Return only a valid JSON object with this shape:
                {
                  "characters": [{"name": "", "role": "", "identity": "", "goal": "", "relationshipHints": [""]}],
                  "events": [{"sourceChapter": "", "summary": "", "consequence": ""}],
                  "conflicts": [{"parties": [""], "conflict": "", "stakes": ""}]
                }
                Do not return Markdown code fences, commentary, or any text outside the JSON object.
                """;
    }

    private String userPrompt(StoryAnalysisInput input) {
        // 保留章节边界，方便后续分场计划追溯每个事件来自哪一章。
        return """
                Title: %s
                Style hint: %s
                Chapters:
                %s
                """.formatted(input.title(), ToolInputs.nullToEmpty(input.styleHint()), formatChapters(input.chapters()));
    }

    private String formatChapters(List<ChapterParseTool.ParsedChapter> chapters) {
        StringBuilder builder = new StringBuilder();
        for (ChapterParseTool.ParsedChapter chapter : chapters) {
            builder.append("[").append(chapter.index()).append("] ")
                    .append(chapter.heading()).append('\n')
                    .append(chapter.content()).append("\n\n");
        }
        return builder.toString().trim();
    }

    public record StoryAnalysisInput(
            String title,
            List<ChapterParseTool.ParsedChapter> chapters,
            String styleHint
    ) {
        /**
         * 防御性拷贝，避免工具执行期间调用方继续修改章节列表。
         */
        public StoryAnalysisInput {
            chapters = List.copyOf(chapters == null ? List.of() : chapters);
        }
    }

    /**
     * 输出：模型返回的原始 JSON，包含 characters、events、conflicts。
     */
    public record StoryAnalysisOutput(String analysisJson) {
    }
}
