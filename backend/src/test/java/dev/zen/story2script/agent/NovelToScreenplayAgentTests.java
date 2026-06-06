package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidator;
import dev.zen.story2script.config.DevFallbackToolLlmClientConfiguration;
import dev.zen.story2script.tools.ChapterParseTool;
import dev.zen.story2script.tools.ScenePlanningTool;
import dev.zen.story2script.tools.ScreenplayYamlWriteTool;
import dev.zen.story2script.tools.StoryAnalysisTool;
import dev.zen.story2script.tools.ToolLlmClient;
import dev.zen.story2script.tools.YamlRepairTool;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class NovelToScreenplayAgentTests {

    @Test
    void completesFullConversionFlowWithMockTools() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "{\"characters\":[],\"events\":[],\"conflicts\":[]}",
                "{\"scenes\":[],\"adaptationNotes\":[]}",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient, NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                "restrained"
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.yaml()).isEqualTo(validYaml());
        assertThat(result.qualityReport().checks()).containsExactly(
                "chapter_parse",
                "story_analysis",
                "scene_planning",
                "yaml_write",
                "yaml_validation"
        );
        assertThat(result.agentTrace().toolCalls()).isEqualTo(5);
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly("chapter_parse", "story_analysis", "scene_planning", "yaml_write", "yaml_validation");
        assertThat(result.warnings()).isEmpty();
        assertThat(llmClient.calls()).hasSize(3);
    }

    @Test
    void fastModeSkipsIntermediateLlmCalls() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(validYaml()));
        NovelToScreenplayAgent agent = agent(llmClient, NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                "restrained",
                "fast"
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.qualityReport().checks()).containsExactly(
                "fast_mode",
                "chapter_parse",
                "yaml_write",
                "yaml_validation"
        );
        assertThat(result.agentTrace().toolCalls()).isEqualTo(3);
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly("chapter_parse", "yaml_write", "yaml_validation");
        assertThat(llmClient.calls()).hasSize(1);
        assertThat(llmClient.calls().getFirst()).contains("Parsed chapters:");
    }

    @Test
    void fastModeCanRepairInvalidYamlOnce() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "schema_version: \"1.0\"",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient, NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null,
                "fast"
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.qualityReport().checks()).containsExactly(
                "fast_mode",
                "chapter_parse",
                "yaml_write",
                "yaml_validation",
                "yaml_repair",
                "yaml_validation_after_repair"
        );
        assertThat(llmClient.calls()).hasSize(2);
    }

    @Test
    void fewerThanThreeChaptersReturnsClearError() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of());
        NovelToScreenplayAgent agent = agent(llmClient, NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS);

        AgentResult result = agent.convert(AgentContext.of(
                "Too Short",
                """
                        Chapter 1 Return
                        A reporter arrives.
                        Chapter 2 Letter
                        A letter appears.
                        """,
                "short_drama",
                null
        ));

        assertThat(result.qualityReport().success()).isFalse();
        assertThat(result.qualityReport().errorCode()).isEqualTo("CHAPTER_PARSE_FAILED");
        assertThat(result.qualityReport().message())
                .contains("at least 3 chapters")
                .contains("found 2");
        assertThat(result.agentTrace().toolCalls()).isEqualTo(1);
        assertThat(result.agentTrace().steps()).singleElement()
                .extracting(AgentResult.Step::tool)
                .isEqualTo("chapter_parse");
        assertThat(llmClient.calls()).isEmpty();
    }

    @Test
    void exceedingMaxStepsReturnsStepLimitError() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "{\"characters\":[],\"events\":[],\"conflicts\":[]}",
                "{\"scenes\":[],\"adaptationNotes\":[]}",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient, 4);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null
        ));

        assertThat(result.qualityReport().success()).isFalse();
        assertThat(result.qualityReport().errorCode()).isEqualTo(NovelToScreenplayAgent.AGENT_STEP_LIMIT_EXCEEDED);
        assertThat(result.warnings()).contains(NovelToScreenplayAgent.AGENT_STEP_LIMIT_EXCEEDED);
        assertThat(result.agentTrace().toolCalls()).isEqualTo(4);
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly("chapter_parse", "story_analysis", "scene_planning", "yaml_write", "step_limit");
    }

    @Test
    void validationFailureCallsRepairAtMostOnce() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "{\"characters\":[],\"events\":[],\"conflicts\":[]}",
                "{\"scenes\":[],\"adaptationNotes\":[]}",
                "schema_version: \"1.0\"",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient, NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.yaml()).isEqualTo(validYaml());
        assertThat(result.qualityReport().checks()).containsExactly(
                "chapter_parse",
                "story_analysis",
                "scene_planning",
                "yaml_write",
                "yaml_validation",
                "yaml_repair",
                "yaml_validation_after_repair"
        );
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly(
                        "chapter_parse",
                        "story_analysis",
                        "scene_planning",
                        "yaml_write",
                        "yaml_validation",
                        "yaml_repair",
                        "yaml_validation"
                );
        assertThat(result.warnings()).contains("YAML was repaired after initial validation failure.");
        assertThat(llmClient.calls()).hasSize(4);
    }

    @Test
    void devFallbackReturnsNonEmptySchemaValidYamlWithClearWarningsAndTrace() {
        ToolLlmClient llmClient = new DevFallbackToolLlmClientConfiguration.DevFallbackToolLlmClient();
        NovelToScreenplayAgent agent = new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new StoryAnalysisTool(llmClient),
                new ScenePlanningTool(llmClient),
                new ScreenplayYamlWriteTool(llmClient),
                new YamlSchemaValidator(),
                new YamlRepairTool(llmClient),
                new RuleBasedAgentPlanner(),
                NovelToScreenplayAgent.DEFAULT_MAX_TOOL_CALLS,
                llmClient.devFallback()
        );

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                "restrained"
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.yaml()).isNotBlank();
        assertThat(new YamlSchemaValidator().validate(result.yaml()).valid()).isTrue();
        assertThat(result.yaml())
                .contains("schema_version")
                .contains("work:")
                .contains("adaptation:")
                .contains("characters:")
                .contains("plot_outline:")
                .contains("scenes:")
                .contains("notes:")
                .contains("Fog Town Letter")
                .contains("short_drama")
                .contains("restrained");
        assertThat(result.warnings())
                .contains("当前使用 dev fallback 演示输出。")
                .contains("未调用真实大模型。")
                .contains("配置 application-local.yml 后可启用真实模型。");
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::summary)
                .anyMatch(summary -> summary.contains("已进入 dev fallback"))
                .anyMatch(summary -> summary.contains("已解析章节"))
                .anyMatch(summary -> summary.contains("已生成示例 YAML"))
                .anyMatch(summary -> summary.contains("未调用真实大模型"));
        assertThat(result.qualityReport().checks())
                .contains("chapterCount=3")
                .contains("characterCount=2")
                .contains("sceneCount=3")
                .contains("reactSteps=7")
                .contains("repaired=false");
    }

    @Test
    void plannerChoosesActionsFromObservationsUntilFinish() {
        RuleBasedAgentPlanner planner = new RuleBasedAgentPlanner();
        AgentState state = new AgentState(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null
        ));

        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.PARSE_CHAPTERS);

        state.observeChapterParse(new ChapterParseTool.ChapterParseOutput(
                true,
                "",
                List.of(
                        new ChapterParseTool.ParsedChapter(1, "Chapter 1", "A"),
                        new ChapterParseTool.ParsedChapter(2, "Chapter 2", "B"),
                        new ChapterParseTool.ParsedChapter(3, "Chapter 3", "C")
                )
        ));
        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.ANALYZE_STORY);

        state.observeStoryAnalysis("{\"characters\":[]}");
        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.PLAN_SCENES);

        state.observeScenePlan("{\"scenes\":[]}");
        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.WRITE_YAML);

        state.observeYamlWrite(validYaml());
        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.VALIDATE_YAML);

        state.observeYamlValidation(new YamlSchemaValidator().validate(validYaml()), "yaml_validation");
        assertThat(planner.decide(state).action()).isEqualTo(AgentAction.FINISH);
    }

    private NovelToScreenplayAgent agent(SequencedLlmClient llmClient, int maxToolCalls) {
        return new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new StoryAnalysisTool(llmClient),
                new ScenePlanningTool(llmClient),
                new ScreenplayYamlWriteTool(llmClient),
                new YamlSchemaValidator(),
                new YamlRepairTool(llmClient),
                maxToolCalls
        );
    }

    private String sourceText() {
        return """
                Chapter 1 Return
                A reporter returns to the fog-covered town.
                Chapter 2 Letter
                An anonymous letter points to an old station.
                Chapter 3 Witness
                An old friend warns her to leave before midnight.
                """;
    }

    private String validYaml() {
        return """
                schema_version: "1.0"
                work:
                  title: "Fog Town Letter"
                  original_author: ""
                  language: "zh-CN"
                  source_chapters:
                    count: 3
                    range: "Chapter 1-3"
                adaptation:
                  target_format: "short_drama"
                  target_duration: "10-15min"
                  genre: "mystery"
                  tone: "restrained"
                  logline: "A reporter returns home after receiving an anonymous letter."
                  principles:
                    - "Keep the main mystery."
                characters:
                  - id: "char_001"
                    name: "Lin Xia"
                    role: "protagonist"
                    identity: "reporter"
                    personality: "controlled"
                    goal: "Find the truth."
                    arc: "From avoidance to confrontation."
                    relationships:
                      - target: "Chen Mo"
                        relation: "old friend"
                plot_outline:
                  - source_chapter: "Chapter 1"
                    key_events:
                      - "Lin Xia receives an anonymous letter."
                    adaptation_choice: "Externalize inner monologue into action."
                scenes:
                  - scene_id: "S001"
                    scene_type: "EXT"
                    location: "Town Station"
                    time_of_day: "NIGHT"
                    characters:
                      - "Lin Xia"
                    summary: "Lin Xia returns to town."
                    dramatic_purpose: "Open the mystery."
                    beats:
                      - type: "action"
                        content: "Fog covers the platform."
                      - type: "dialogue"
                        speaker: "Chen Mo"
                        content: "You should not have come back."
                notes:
                  adaptation_summary: "The opening chapters are compressed into one scene."
                  omitted_elements:
                    - "Minor backstory."
                  risks:
                    - "Some motives need polish."
                  next_steps:
                    - "Add a stronger ending hook."
                """;
    }

    private static final class SequencedLlmClient implements ToolLlmClient {

        private final Queue<String> responses;
        private final List<String> calls = new ArrayList<>();

        private SequencedLlmClient(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            calls.add(userPrompt);
            if (responses.isEmpty()) {
                throw new IllegalStateException("No mock LLM response configured.");
            }
            return responses.remove();
        }

        private List<String> calls() {
            return calls;
        }
    }
}
