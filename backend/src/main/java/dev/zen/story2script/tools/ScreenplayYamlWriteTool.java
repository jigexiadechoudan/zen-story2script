package dev.zen.story2script.tools;

import dev.zen.story2script.schema.ScreenplayYamlSchema;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 剧本 YAML 草稿生成工具，使用 LLM 把分析结果和分场计划写成 YAML。
 *
 * <p>Prompt 会引用现有 schema 常量作为输出约束，但这个类不修改 schema，
 * 也不执行 YAML 校验。</p>
 */
@Component
public class ScreenplayYamlWriteTool {

    private final ToolLlmClient llmClient;

    public ScreenplayYamlWriteTool(ToolLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 把故事分析 JSON 和分场计划 JSON 合成为剧本 YAML 草稿。
     *
     * <p>Prompt 明确要求不要返回 Markdown 代码块，因为调用方需要直接把返回文本
     * 交给 YAML 校验或修复流程。</p>
     */
    @Tool(description = "Write a screenplay YAML draft from story analysis and scene plan.")
    public ScreenplayYamlWriteOutput write(ScreenplayYamlWriteInput input) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.title(), "title");
        ToolInputs.requireText(input.analysisJson(), "analysisJson");
        ToolInputs.requireText(input.scenePlanJson(), "scenePlanJson");

        return new ScreenplayYamlWriteOutput(llmClient.generate(systemPrompt(), userPrompt(input)));
    }

    /**
     * 快速生成模式：把章节摘要、人物抽取、分场规划和 YAML 写作合并到一次模型调用。
     *
     * <p>它用于 MVP 演示和浏览器联调，目标是降低端到端等待时间。完整 ReAct 链路仍然保留在
     * {@link #write(ScreenplayYamlWriteInput)} 所使用的多步流程中。</p>
     */
    public ScreenplayYamlWriteOutput writeFast(FastScreenplayYamlWriteInput input) {
        input = ToolInputs.requireInput(input);
        ToolInputs.requireText(input.title(), "title");
        if (input.chapters() == null || input.chapters().isEmpty()) {
            throw new IllegalArgumentException("chapters must not be empty");
        }

        return new ScreenplayYamlWriteOutput(llmClient.generate(fastSystemPrompt(), fastUserPrompt(input)));
    }

    private String systemPrompt() {
        return """
                You are a screenplay YAML writing tool.
                Return only YAML text that follows schema_version "%s".
                Top-level fields must be exactly: %s.
                Scene scene_type values must be one of: %s.
                Beat type values must be one of: %s.
                Do not return Markdown code fences, commentary, or any text outside the YAML document.
                """.formatted(
                ScreenplayYamlSchema.VERSION,
                ScreenplayYamlSchema.TOP_LEVEL_FIELDS,
                ScreenplayYamlSchema.SCENE_TYPES,
                ScreenplayYamlSchema.BEAT_TYPES
        );
    }

    private String userPrompt(ScreenplayYamlWriteInput input) {
        // 可选元数据为 null 时写成空字符串，保持 Prompt 结构稳定。
        return """
                Title: %s
                Author: %s
                Language: %s
                Target format: %s
                Target duration: %s
                Style hint: %s
                Story analysis JSON:
                %s
                Scene plan JSON:
                %s
                """.formatted(
                input.title(),
                ToolInputs.nullToEmpty(input.originalAuthor()),
                ToolInputs.nullToEmpty(input.language()),
                ToolInputs.nullToEmpty(input.targetFormat()),
                ToolInputs.nullToEmpty(input.targetDuration()),
                ToolInputs.nullToEmpty(input.styleHint()),
                input.analysisJson(),
                input.scenePlanJson()
        );
    }

    private String fastSystemPrompt() {
        return """
                You are a fast novel-to-screenplay YAML agent for an MVP web demo.
                Complete the adaptation in one pass: extract the minimal character list, outline key plot events,
                plan compact scenes, then return only YAML text that follows schema_version "%s".

                Hard constraints:
                - Top-level fields must be exactly: %s.
                - Scene scene_type values must be one of: %s.
                - Beat type values must be one of: %s.
                - Prefer 3 to 6 scenes for short_drama, 4 to 8 scenes for screenplay, and 3 to 8 outline scenes.
                - Each scene should contain 2 to 4 beats only.
                - Keep dialogue concise. Avoid long monologues.
                - Do not return Markdown code fences, commentary, or any text outside the YAML document.
                """.formatted(
                ScreenplayYamlSchema.VERSION,
                ScreenplayYamlSchema.TOP_LEVEL_FIELDS,
                ScreenplayYamlSchema.SCENE_TYPES,
                ScreenplayYamlSchema.BEAT_TYPES
        );
    }

    private String fastUserPrompt(FastScreenplayYamlWriteInput input) {
        return """
                Title: %s
                Author: %s
                Language: %s
                Target format: %s
                Target duration: %s
                Style hint: %s

                Parsed chapters:
                %s
                """.formatted(
                input.title(),
                ToolInputs.nullToEmpty(input.originalAuthor()),
                ToolInputs.nullToEmpty(input.language()),
                ToolInputs.nullToEmpty(input.targetFormat()),
                ToolInputs.nullToEmpty(input.targetDuration()),
                ToolInputs.nullToEmpty(input.styleHint()),
                formatChapters(input.chapters())
        );
    }

    private String formatChapters(java.util.List<ChapterParseTool.ParsedChapter> chapters) {
        StringBuilder builder = new StringBuilder();
        for (ChapterParseTool.ParsedChapter chapter : chapters) {
            builder.append("[").append(chapter.index()).append("] ")
                    .append(chapter.heading()).append('\n')
                    .append(chapter.content()).append("\n\n");
        }
        return builder.toString().trim();
    }

    public record ScreenplayYamlWriteInput(
            String title,
            String originalAuthor,
            String language,
            String targetFormat,
            String targetDuration,
            String styleHint,
            String analysisJson,
            String scenePlanJson
    ) {
    }

    public record FastScreenplayYamlWriteInput(
            String title,
            String originalAuthor,
            String language,
            String targetFormat,
            String targetDuration,
            String styleHint,
            java.util.List<ChapterParseTool.ParsedChapter> chapters
    ) {
        public FastScreenplayYamlWriteInput {
            chapters = java.util.List.copyOf(chapters == null ? java.util.List.of() : chapters);
        }
    }

    /**
     * 输出：模型返回的原始剧本 YAML 文本。
     */
    public record ScreenplayYamlWriteOutput(String yaml) {
    }
}
