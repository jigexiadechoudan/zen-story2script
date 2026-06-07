package dev.zen.story2script.api.service;

import dev.zen.story2script.agent.AgentContext;
import dev.zen.story2script.agent.AgentResult;
import dev.zen.story2script.agent.NovelToScreenplayAgent;
import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import dev.zen.story2script.schema.ScreenplayYamlSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentNovelToScreenplayServiceTests {

    @Test
    void mapsAgentResultToApiResponse() {
        NovelToScreenplayAgent agent = mock(NovelToScreenplayAgent.class);
        when(agent.convert(any(AgentContext.class))).thenReturn(new AgentResult(
                "schema_version: \"1.0\"",
                AgentResult.QualityReport.success(
                        List.of("chapter_parse", "yaml_validation", "chapterCount=3", "sceneCount=3"),
                        List.of()
                ),
                new AgentResult.AgentTrace(
                        "react",
                        List.of(new AgentResult.Step(1, "yaml_validation", "YAML validation passed.")),
                        1
                ),
                List.of("sample_warning")
        ));
        AgentNovelToScreenplayService service = new AgentNovelToScreenplayService(agent);

        ConvertResponse response = service.convert(new ConvertRequest(
                "Fog Town Letter",
                "Chapter 1\nA\nChapter 2\nB\nChapter 3\nC",
                "short_drama",
                "restrained",
                "react",
                "en"
        ));

        assertThat(response.yaml()).isEqualTo("schema_version: \"1.0\"");
        assertThat(response.schemaVersion()).isEqualTo(ScreenplayYamlSchema.VERSION);
        assertThat(response.warnings()).containsExactly("sample_warning");
        assertThat(response.qualityReport().confidence()).isEqualTo(1.0);
        assertThat(response.qualityReport().checks())
                .containsExactly("chapter_parse", "yaml_validation", "chapterCount=3", "sceneCount=3");
        assertThat(response.agentTrace().mode()).isEqualTo("react");
        assertThat(response.agentTrace().steps()).containsExactly("1. yaml_validation: YAML validation passed.");
    }

    @Test
    void defaultsApiRequestsToFastConversionMode() {
        NovelToScreenplayAgent agent = mock(NovelToScreenplayAgent.class);
        when(agent.convert(any(AgentContext.class))).thenReturn(new AgentResult(
                "schema_version: \"1.0\"",
                AgentResult.QualityReport.success(List.of("fast_mode"), List.of()),
                new AgentResult.AgentTrace("react", List.of(), 0),
                List.of()
        ));
        AgentNovelToScreenplayService service = new AgentNovelToScreenplayService(agent);

        service.convert(new ConvertRequest(
                "Fog Town Letter",
                "Chapter 1\nA\nChapter 2\nB\nChapter 3\nC",
                "short_drama",
                "restrained",
                null,
                null
        ));

        verify(agent).convert(org.mockito.ArgumentMatchers.argThat(AgentContext::fastMode));
    }

    @Test
    void exposesFriendlyChapterParseFailureInWarningsAndQualityChecks() {
        NovelToScreenplayAgent agent = mock(NovelToScreenplayAgent.class);
        String message = "未能识别到足够章节。请至少提供 3 章，并使用‘第一章’‘第1章’或‘Chapter 1’这类章节标题。当前识别到 2 章。";
        when(agent.convert(any(AgentContext.class))).thenReturn(new AgentResult(
                "",
                AgentResult.QualityReport.failure(
                        "CHAPTER_PARSE_FAILED",
                        message,
                        List.of("chapter_parse"),
                        List.of()
                ),
                new AgentResult.AgentTrace(
                        "react",
                        List.of(new AgentResult.Step(1, "chapter_parse", "Parsed 2 chapters; valid=false.")),
                        1
                ),
                List.of(message)
        ));
        AgentNovelToScreenplayService service = new AgentNovelToScreenplayService(agent);

        ConvertResponse response = service.convert(new ConvertRequest(
                "Too Short",
                "Chapter 1\nA\nChapter 2\nB",
                "short_drama",
                null,
                "react",
                null
        ));

        assertThat(response.warnings())
                .contains("CHAPTER_PARSE_FAILED")
                .anySatisfy(warning -> assertThat(warning)
                        .contains("至少提供 3 章")
                        .contains("当前识别到 2 章"));
        assertThat(response.qualityReport().checks())
                .contains("chapter_parse")
                .anySatisfy(check -> assertThat(check)
                        .contains("至少提供 3 章")
                        .contains("当前识别到 2 章"));
    }

    @Test
    void mapsRequestLanguageToAgentContext() {
        NovelToScreenplayAgent agent = mock(NovelToScreenplayAgent.class);
        when(agent.convert(any(AgentContext.class))).thenReturn(new AgentResult(
                "schema_version: \"1.0\"",
                AgentResult.QualityReport.success(List.of("fast_mode"), List.of()),
                new AgentResult.AgentTrace("fast", List.of(), 0),
                List.of()
        ));
        AgentNovelToScreenplayService service = new AgentNovelToScreenplayService(agent);

        service.convert(new ConvertRequest(
                "Fog Town Letter",
                "Chapter 1\nA\nChapter 2\nB\nChapter 3\nC",
                "short_drama",
                "restrained",
                "fast",
                "en"
        ));

        verify(agent).convert(org.mockito.ArgumentMatchers.argThat(context -> "en-US".equals(context.language())));
    }
}
