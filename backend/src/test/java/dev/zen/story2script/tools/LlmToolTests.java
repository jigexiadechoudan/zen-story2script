package dev.zen.story2script.tools;

import dev.zen.story2script.schema.YamlSchemaValidationError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmToolTests {

    private final CapturingLlmClient llmClient = new CapturingLlmClient("MODEL_OUTPUT");

    @Test
    void storyAnalysisToolCallsMockableLlmClient() {
        StoryAnalysisTool tool = new StoryAnalysisTool(llmClient);
        List<ChapterParseTool.ParsedChapter> chapters = List.of(
                new ChapterParseTool.ParsedChapter(1, "第1章", "林夏回到雾镇。"),
                new ChapterParseTool.ParsedChapter(2, "第一章", "匿名信出现。"),
                new ChapterParseTool.ParsedChapter(3, "Chapter 1", "陈默阻止她。")
        );

        StoryAnalysisTool.StoryAnalysisOutput output = tool.analyze(
                new StoryAnalysisTool.StoryAnalysisInput("雾镇来信", chapters, "悬疑")
        );

        assertThat(output.analysisJson()).isEqualTo("MODEL_OUTPUT");
        assertThat(llmClient.systemPrompts()).singleElement()
                .asString()
                .contains("characters")
                .contains("events")
                .contains("conflicts")
                .contains("Do not return Markdown code fences");
        assertThat(llmClient.userPrompts()).singleElement()
                .asString()
                .contains("雾镇来信")
                .contains("林夏回到雾镇");
    }

    @Test
    void scenePlanningToolCallsMockableLlmClient() {
        ScenePlanningTool tool = new ScenePlanningTool(llmClient);

        ScenePlanningTool.ScenePlanningOutput output = tool.plan(
                new ScenePlanningTool.ScenePlanningInput("雾镇来信", "{\"events\":[]}", "short_drama", "10min")
        );

        assertThat(output.scenePlanJson()).isEqualTo("MODEL_OUTPUT");
        assertThat(llmClient.systemPrompts()).singleElement()
                .asString()
                .contains("scenes")
                .contains("sceneId")
                .contains("Do not return Markdown code fences");
        assertThat(llmClient.userPrompts()).singleElement()
                .asString()
                .contains("{\"events\":[]}");
    }

    @Test
    void screenplayYamlWriteToolCallsMockableLlmClient() {
        ScreenplayYamlWriteTool tool = new ScreenplayYamlWriteTool(llmClient);

        ScreenplayYamlWriteTool.ScreenplayYamlWriteOutput output = tool.write(
                new ScreenplayYamlWriteTool.ScreenplayYamlWriteInput(
                        "雾镇来信",
                        "佚名",
                        "zh-CN",
                        "short_drama",
                        "10min",
                        "克制",
                        "{\"characters\":[]}",
                        "{\"scenes\":[]}"
                )
        );

        assertThat(output.yaml()).isEqualTo("MODEL_OUTPUT");
        assertThat(llmClient.systemPrompts()).singleElement()
                .asString()
                .contains("schema_version")
                .contains("1.0")
                .contains("Do not return Markdown code fences");
        assertThat(llmClient.userPrompts()).singleElement()
                .asString()
                .contains("{\"characters\":[]}")
                .contains("{\"scenes\":[]}");
    }

    @Test
    void yamlRepairToolCallsMockableLlmClientWithValidationErrors() {
        YamlRepairTool tool = new YamlRepairTool(llmClient);
        List<YamlSchemaValidationError> errors = List.of(
                new YamlSchemaValidationError("$.scenes", "MISSING_TOP_LEVEL_FIELD", "scenes is required")
        );

        YamlRepairTool.YamlRepairOutput output = tool.repair(
                new YamlRepairTool.YamlRepairInput("schema_version: 1.0", errors)
        );

        assertThat(output.yaml()).isEqualTo("MODEL_OUTPUT");
        assertThat(llmClient.systemPrompts()).singleElement()
                .asString()
                .contains("Return only the repaired YAML document")
                .contains("Do not return Markdown code fences");
        assertThat(llmClient.userPrompts()).singleElement()
                .asString()
                .contains("MISSING_TOP_LEVEL_FIELD")
                .contains("schema_version: 1.0");
    }

    private static class CapturingLlmClient implements ToolLlmClient {

        private final String response;
        private final List<String> systemPrompts = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();

        private CapturingLlmClient(String response) {
            this.response = response;
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            return response;
        }

        private List<String> systemPrompts() {
            return systemPrompts;
        }

        private List<String> userPrompts() {
            return userPrompts;
        }
    }
}
