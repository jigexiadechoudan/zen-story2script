package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidator;
import dev.zen.story2script.config.DevFallbackToolLlmClientConfiguration;
import dev.zen.story2script.tools.ChapterParseTool;
import dev.zen.story2script.tools.ScreenplayYamlWriteTool;
import dev.zen.story2script.tools.ScreenplayQualityReviewTool;
import dev.zen.story2script.tools.SourceCoverageAuditTool;
import dev.zen.story2script.tools.ToolLlmClient;
import dev.zen.story2script.tools.YamlRepairTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class NovelToScreenplayAgentTests {

    @Test
    void reactModeRunsWithToolLlmClientWhenChatClientBeanIsUnavailable() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "{\"thought\":\"skip optional audit\",\"action\":\"finish\",\"repair_hint\":\"\"}",
                validYaml(),
                "{\"thought\":\"quality is sufficient\",\"action\":\"finish\",\"repair_hint\":\"\"}"
        ));
        NovelToScreenplayAgent agent = autonomousAgent(llmClient);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                "restrained"
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.yaml()).isEqualTo(validYaml());
        assertThat(result.qualityReport().checks()).contains(
                "bounded_react",
                "chapter_parse",
                "react_think_before_write",
                "yaml_write",
                "yaml_validation",
                "react_think_after_write"
        ).doesNotContain("fast_mode");
        assertThat(result.agentTrace().mode()).isEqualTo("react");
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly(
                        "chapter_parse",
                        "react_think",
                        "react_finish",
                        "yaml_write",
                        "yaml_validation",
                        "react_think",
                        "react_finish"
                );
        assertThat(result.warnings()).isEmpty();
        assertThat(llmClient.calls()).hasSize(3);
        assertThat(llmClient.calls().getFirst()).contains("Phase: before write");
        assertThat(llmClient.calls().get(1)).contains("Parsed chapters:");
    }

    @Test
    void fastModeSkipsIntermediateLlmCalls() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(validYaml()));
        NovelToScreenplayAgent agent = agent(llmClient);

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
        assertThat(result.agentTrace().mode()).isEqualTo("fast");
        assertThat(result.agentTrace().toolCalls()).isEqualTo(3);
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly("chapter_parse", "yaml_write", "yaml_validation");
        assertThat(llmClient.calls()).hasSize(1);
        assertThat(llmClient.calls().getFirst()).contains("Parsed chapters:");
    }

    @Test
    void reactModeRunsBoundedThinkAuditLoopAndUsesRagAdvisors() {
        AdvisorCapturingLlmClient llmClient = new AdvisorCapturingLlmClient(List.of(
                "{\"thought\":\"check source coverage first\",\"action\":\"source_coverage_audit\",\"repair_hint\":\"\"}",
                validThreeSceneYaml(),
                "{\"thought\":\"review the generated draft\",\"action\":\"screenplay_quality_review\",\"repair_hint\":\"\"}",
                validThreeSceneYaml()
        ));
        Advisor advisor = testAdvisor();
        NovelToScreenplayAgent agent = new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new ScreenplayYamlWriteTool(provider(llmClient)),
                new SourceCoverageAuditTool(),
                new ScreenplayQualityReviewTool(),
                new YamlSchemaValidator(),
                new YamlRepairTool(provider(llmClient)),
                llmClient,
                false,
                List.of(advisor),
                true,
                Duration.ofSeconds(10),
                12_000
        );

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                "restrained",
                "react"
        ));

        assertThat(result.qualityReport().success())
                .describedAs("message=%s checks=%s warnings=%s steps=%s yaml=%s",
                        result.qualityReport().message(),
                        result.qualityReport().checks(),
                        result.warnings(),
                        result.agentTrace().steps(),
                        result.yaml())
                .isTrue();
        assertThat(result.agentTrace().mode()).isEqualTo("react");
        assertThat(result.qualityReport().checks())
                .contains(
                        "bounded_react",
                        "rag_advisor_attached",
                        "react_think_before_write",
                        "source_coverage_audit",
                        "react_think_after_write",
                        "screenplay_quality_review"
                )
                .doesNotContain("fast_mode");
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsSequence(
                        "chapter_parse",
                        "react_think",
                        "source_coverage_audit",
                        "yaml_write",
                        "yaml_validation",
                        "react_think",
                        "screenplay_quality_review"
                );
        assertThat(llmClient.advisorCounts()).containsExactly(1, 1, 1, 1);
    }

    @Test
    void fastModeCanRepairInvalidYamlOnce() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "schema_version: \"1.0\"",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient);

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
        NovelToScreenplayAgent agent = agent(llmClient);

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
                .contains("至少提供 3 章")
                .contains("当前识别到 2 章");
        assertThat(result.qualityReport().checks()).contains("chapter_parse");
        assertThat(result.warnings()).singleElement()
                .asString()
                .contains("至少提供 3 章")
                .contains("当前识别到 2 章");
        assertThat(result.agentTrace().toolCalls()).isEqualTo(1);
        assertThat(result.agentTrace().steps()).singleElement()
                .extracting(AgentResult.Step::tool)
                .isEqualTo("chapter_parse");
        assertThat(llmClient.calls()).isEmpty();
    }

    @Test
    void reactFallbackAlwaysUsesFastWhenAutonomousDependenciesAreUnavailable() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(validYaml()));
        NovelToScreenplayAgent agent = agent(llmClient);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.qualityReport().errorCode()).isEmpty();
        assertThat(result.agentTrace().mode()).isEqualTo("fast");
        assertThat(result.agentTrace().toolCalls()).isEqualTo(3);
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly("chapter_parse", "yaml_write", "yaml_validation");
    }

    @Test
    void validationFailureCallsRepairAtMostOnce() {
        SequencedLlmClient llmClient = new SequencedLlmClient(List.of(
                "schema_version: \"1.0\"",
                validYaml()
        ));
        NovelToScreenplayAgent agent = agent(llmClient);

        AgentResult result = agent.convert(AgentContext.of(
                "Fog Town Letter",
                sourceText(),
                "short_drama",
                null
        ));

        assertThat(result.qualityReport().success()).isTrue();
        assertThat(result.yaml()).isEqualTo(validYaml());
        assertThat(result.qualityReport().checks()).containsExactly(
                "fast_mode",
                "chapter_parse",
                "yaml_write",
                "yaml_validation",
                "yaml_repair",
                "yaml_validation_after_repair"
        );
        assertThat(result.agentTrace().steps())
                .extracting(AgentResult.Step::tool)
                .containsExactly(
                        "chapter_parse",
                        "yaml_write",
                        "yaml_validation",
                        "yaml_repair",
                        "yaml_validation"
                );
        assertThat(result.warnings()).contains("YAML was repaired after initial validation failure.");
        assertThat(llmClient.calls()).hasSize(2);
    }

    @Test
    void devFallbackReturnsNonEmptySchemaValidYamlWithClearWarningsAndTrace() {
        ToolLlmClient llmClient = new DevFallbackToolLlmClientConfiguration.DevFallbackToolLlmClient();
        NovelToScreenplayAgent agent = new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new ScreenplayYamlWriteTool(provider(llmClient)),
                new YamlSchemaValidator(),
                new YamlRepairTool(provider(llmClient)),
                llmClient,
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
                .anyMatch(summary -> summary.contains("YAML"))
                .anyMatch(summary -> summary.contains("未调用真实大模型"));
        assertThat(result.qualityReport().checks())
                .contains("chapterCount=3")
                .contains("characterCount=2")
                .contains("sceneCount=3")
                .contains("reactSteps=5")
                .contains("repaired=false");
    }

    private NovelToScreenplayAgent agent(SequencedLlmClient llmClient) {
        return new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new ScreenplayYamlWriteTool(provider(llmClient)),
                new YamlSchemaValidator(),
                new YamlRepairTool(provider(llmClient)),
                llmClient,
                false
        );
    }

    private NovelToScreenplayAgent autonomousAgent(ToolLlmClient llmClient) {
        return new NovelToScreenplayAgent(
                new ChapterParseTool(),
                new ScreenplayYamlWriteTool(provider(llmClient)),
                new SourceCoverageAuditTool(),
                new ScreenplayQualityReviewTool(),
                new YamlSchemaValidator(),
                new YamlRepairTool(provider(llmClient)),
                llmClient,
                false,
                List.of(),
                true,
                Duration.ofSeconds(10),
                12_000
        );
    }

    private Advisor testAdvisor() {
        return new Advisor() {
            @Override
            public String getName() {
                return "test-rag-advisor";
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
    }

    private ObjectProvider<ToolLlmClient> provider(ToolLlmClient llmClient) {
        return new ObjectProvider<>() {
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
        };
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

    private String validThreeSceneYaml() {
        return validYaml()
                .replace("dramatic_purpose: \"Open the mystery.\"", "dramatic_purpose: \"Open the mystery under pressure.\"")
                .replace(
                        "                    beats:\n"
                                + "                      - type: \"action\"\n"
                                + "                        content: \"Fog covers the platform.\"\n"
                                + "                      - type: \"dialogue\"\n"
                                + "                        speaker: \"Chen Mo\"\n"
                                + "                        content: \"You should not have come back.\"\n",
                        "                    beats:\n"
                                + "                      - type: \"action\"\n"
                                + "                        content: \"Fog covers the platform.\"\n"
                                + "                      - type: \"dialogue\"\n"
                                + "                        speaker: \"Chen Mo\"\n"
                                + "                        content: \"You should not have come back.\"\n"
                                + "                      - type: \"action\"\n"
                                + "                        content: \"Lin Xia hides the letter inside her coat before answering.\"\n"
                )
                .replace(
                "                notes:\n",
                """
                  - scene_id: "S002"
                    scene_type: "INT"
                    location: "Old Station Office"
                    time_of_day: "NIGHT"
                    characters:
                      - "Lin Xia"
                      - "Chen Mo"
                    summary: "Lin Xia confronts Chen Mo under pressure."
                    dramatic_purpose: "Escalate conflict and force a choice."
                    beats:
                      - type: "action"
                        content: "Lin Xia locks the office door and lays the letter on the desk."
                      - type: "dialogue"
                        speaker: "Lin Xia"
                        content: "Tell me whose handwriting this is."
                      - type: "dialogue"
                        speaker: "Chen Mo"
                        content: "If I say it, neither of us leaves before midnight."
                  - scene_id: "S003"
                    scene_type: "EXT"
                    location: "Abandoned Platform"
                    time_of_day: "NIGHT"
                    characters:
                      - "Lin Xia"
                      - "Chen Mo"
                    summary: "The final clue creates a direct threat."
                    dramatic_purpose: "Resolve the immediate conflict with a risky choice."
                    beats:
                      - type: "action"
                        content: "A station clock starts moving although the power is out."
                      - type: "dialogue"
                        speaker: "Chen Mo"
                        content: "Choose now, the truth or your safety."
                      - type: "action"
                        content: "Lin Xia pockets the letter and steps toward the dark track."
                notes:
                """
        );
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

    private static final class AdvisorCapturingLlmClient implements ToolLlmClient {

        private final Queue<String> responses;
        private final List<Integer> advisorCounts = new ArrayList<>();

        private AdvisorCapturingLlmClient(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            if (responses.isEmpty()) {
                throw new IllegalStateException("No mock LLM response configured.");
            }
            return responses.remove();
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, List<Advisor> advisors) {
            advisorCounts.add(advisors == null ? 0 : advisors.size());
            return generate(systemPrompt, userPrompt);
        }

        private List<Integer> advisorCounts() {
            return advisorCounts;
        }
    }
}
