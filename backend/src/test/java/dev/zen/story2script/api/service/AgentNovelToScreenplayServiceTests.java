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
                "restrained"
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
}
