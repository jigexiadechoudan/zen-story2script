package dev.zen.story2script.api.service;

import dev.zen.story2script.agent.AgentContext;
import dev.zen.story2script.agent.AgentResult;
import dev.zen.story2script.agent.NovelToScreenplayAgent;
import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import dev.zen.story2script.schema.ScreenplayYamlSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * API-facing adapter for the real novel-to-screenplay agent.
 */
@Service
class AgentNovelToScreenplayService implements NovelToScreenplayService {

    private final NovelToScreenplayAgent agent;

    AgentNovelToScreenplayService(NovelToScreenplayAgent agent) {
        this.agent = agent;
    }

    @Override
    public ConvertResponse convert(ConvertRequest request) {
        AgentResult result = agent.convert(
                AgentContext.of(request.title(), request.sourceText(), request.targetFormat(), request.styleHint())
        );

        return new ConvertResponse(
                result.yaml(),
                ScreenplayYamlSchema.VERSION,
                warnings(result),
                qualityReport(result.qualityReport()),
                agentTrace(result.agentTrace())
        );
    }

    private List<String> warnings(AgentResult result) {
        List<String> warnings = new ArrayList<>(result.warnings());
        if (!result.qualityReport().success() && !result.qualityReport().errorCode().isBlank()) {
            warnings.add(result.qualityReport().errorCode());
        }
        return List.copyOf(warnings);
    }

    private ConvertResponse.QualityReport qualityReport(AgentResult.QualityReport qualityReport) {
        List<String> checks = new ArrayList<>(qualityReport.checks());
        if (!qualityReport.success() && !qualityReport.message().isBlank()) {
            checks.add(qualityReport.message());
        }
        return new ConvertResponse.QualityReport(qualityReport.success() ? 1.0 : 0.0, List.copyOf(checks));
    }

    private ConvertResponse.AgentTrace agentTrace(AgentResult.AgentTrace agentTrace) {
        List<String> steps = agentTrace.steps().stream()
                .map(step -> "%d. %s: %s".formatted(step.index(), step.tool(), step.summary()))
                .toList();
        return new ConvertResponse.AgentTrace(agentTrace.mode(), steps);
    }
}
