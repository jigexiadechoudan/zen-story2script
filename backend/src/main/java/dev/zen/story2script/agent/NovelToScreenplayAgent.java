package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.schema.YamlSchemaValidator;
import dev.zen.story2script.tools.ChapterParseTool;
import dev.zen.story2script.tools.ScenePlanningTool;
import dev.zen.story2script.tools.ScreenplayYamlWriteTool;
import dev.zen.story2script.tools.StoryAnalysisTool;
import dev.zen.story2script.tools.YamlRepairTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ReAct loop for converting a novel excerpt into screenplay YAML.
 */
@Service
public class NovelToScreenplayAgent {

    public static final int DEFAULT_MAX_TOOL_CALLS = 8;
    public static final String AGENT_STEP_LIMIT_EXCEEDED = "AGENT_STEP_LIMIT_EXCEEDED";

    private final ChapterParseTool chapterParseTool;
    private final StoryAnalysisTool storyAnalysisTool;
    private final ScenePlanningTool scenePlanningTool;
    private final ScreenplayYamlWriteTool screenplayYamlWriteTool;
    private final YamlSchemaValidator yamlSchemaValidator;
    private final YamlRepairTool yamlRepairTool;
    private final AgentPlanner planner;
    private final int maxToolCalls;

    @Autowired
    public NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            StoryAnalysisTool storyAnalysisTool,
            ScenePlanningTool scenePlanningTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlRepairTool yamlRepairTool,
            @Value("${story2script.agent.max-steps:8}") int maxToolCalls
    ) {
        this(
                chapterParseTool,
                storyAnalysisTool,
                scenePlanningTool,
                screenplayYamlWriteTool,
                new YamlSchemaValidator(),
                yamlRepairTool,
                new RuleBasedAgentPlanner(),
                maxToolCalls
        );
    }

    NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            StoryAnalysisTool storyAnalysisTool,
            ScenePlanningTool scenePlanningTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlSchemaValidator yamlSchemaValidator,
            YamlRepairTool yamlRepairTool,
            int maxToolCalls
    ) {
        this(
                chapterParseTool,
                storyAnalysisTool,
                scenePlanningTool,
                screenplayYamlWriteTool,
                yamlSchemaValidator,
                yamlRepairTool,
                new RuleBasedAgentPlanner(),
                maxToolCalls
        );
    }

    NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            StoryAnalysisTool storyAnalysisTool,
            ScenePlanningTool scenePlanningTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlSchemaValidator yamlSchemaValidator,
            YamlRepairTool yamlRepairTool,
            AgentPlanner planner,
            int maxToolCalls
    ) {
        if (maxToolCalls < 1) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
        this.chapterParseTool = chapterParseTool;
        this.storyAnalysisTool = storyAnalysisTool;
        this.scenePlanningTool = scenePlanningTool;
        this.screenplayYamlWriteTool = screenplayYamlWriteTool;
        this.yamlSchemaValidator = yamlSchemaValidator;
        this.yamlRepairTool = yamlRepairTool;
        this.planner = planner;
        this.maxToolCalls = maxToolCalls;
    }

    public AgentResult convert(AgentContext context) {
        AgentState state = new AgentState(context);

        try {
            while (true) {
                AgentDecision decision = planner.decide(state);
                if (decision.action() == AgentAction.FINISH) {
                    return finish(state);
                }

                if (!canCallTool(state)) {
                    return stepLimitResult(state, decision.action());
                }

                execute(decision, state);
            }
        } catch (IllegalArgumentException ex) {
            state.warn(ex.getMessage());
            return failureResult("", "AGENT_INPUT_INVALID", ex.getMessage(), state, List.of());
        } catch (RuntimeException ex) {
            String message = "Agent tool execution failed: " + ex.getMessage();
            state.warn(message);
            return failureResult("", "AGENT_TOOL_FAILED", message, state, List.of());
        }
    }

    private void execute(AgentDecision decision, AgentState state) {
        switch (decision.action()) {
            case PARSE_CHAPTERS -> parseChapters(state);
            case ANALYZE_STORY -> analyzeStory(state);
            case PLAN_SCENES -> planScenes(state);
            case WRITE_YAML -> writeYaml(state);
            case VALIDATE_YAML -> validateYaml(state);
            case REPAIR_YAML -> repairYaml(state);
            case FINISH -> throw new IllegalStateException("FINISH is not executable as a tool.");
        }
    }

    private void parseChapters(AgentState state) {
        state.recordToolCall(AgentAction.PARSE_CHAPTERS.traceName(), "Parsed source text into chapters.");
        ChapterParseTool.ChapterParseOutput output = chapterParseTool.parse(
                new ChapterParseTool.ChapterParseInput(state.context().sourceText())
        );
        state.observeChapterParse(output);
        state.replaceLastTraceSummary("Parsed %d chapters; valid=%s.".formatted(output.chapters().size(), output.valid()));
        if (!output.valid()) {
            state.warn(output.errorMessage());
        }
    }

    private void analyzeStory(AgentState state) {
        state.recordToolCall(AgentAction.ANALYZE_STORY.traceName(), "Analyzed characters, events, and conflicts.");
        StoryAnalysisTool.StoryAnalysisOutput output = storyAnalysisTool.analyze(
                new StoryAnalysisTool.StoryAnalysisInput(
                        state.context().title(),
                        state.chapterParseOutput().chapters(),
                        state.context().styleHint()
                )
        );
        state.observeStoryAnalysis(output.analysisJson());
    }

    private void planScenes(AgentState state) {
        state.recordToolCall(AgentAction.PLAN_SCENES.traceName(), "Planned screenplay scenes from story analysis.");
        ScenePlanningTool.ScenePlanningOutput output = scenePlanningTool.plan(
                new ScenePlanningTool.ScenePlanningInput(
                        state.context().title(),
                        state.analysisJson(),
                        state.context().targetFormat(),
                        state.context().targetDuration()
                )
        );
        state.observeScenePlan(output.scenePlanJson());
    }

    private void writeYaml(AgentState state) {
        state.recordToolCall(AgentAction.WRITE_YAML.traceName(), "Generated screenplay YAML draft.");
        ScreenplayYamlWriteTool.ScreenplayYamlWriteOutput output = screenplayYamlWriteTool.write(
                new ScreenplayYamlWriteTool.ScreenplayYamlWriteInput(
                        state.context().title(),
                        state.context().originalAuthor(),
                        state.context().language(),
                        state.context().targetFormat(),
                        state.context().targetDuration(),
                        state.context().styleHint(),
                        state.analysisJson(),
                        state.scenePlanJson()
                )
        );
        state.observeYamlWrite(output.yaml());
    }

    private void validateYaml(AgentState state) {
        state.recordToolCall(AgentAction.VALIDATE_YAML.traceName(), "Validated screenplay YAML.");
        YamlSchemaValidationResult output = yamlSchemaValidator.validate(state.yaml());
        String checkName = state.checks().contains("yaml_repair")
                ? "yaml_validation_after_repair"
                : "yaml_validation";
        state.observeYamlValidation(output, checkName);
        state.replaceLastTraceSummary(validationSummary(output));
    }

    private void repairYaml(AgentState state) {
        state.warn("YAML validation failed; attempting one repair.");
        state.recordToolCall(AgentAction.REPAIR_YAML.traceName(), "Repaired screenplay YAML after validation failure.");
        YamlRepairTool.YamlRepairOutput output = yamlRepairTool.repair(
                new YamlRepairTool.YamlRepairInput(state.yaml(), state.validationResult().errors())
        );
        state.observeYamlRepair(output.yaml());
    }

    private AgentResult finish(AgentState state) {
        if (state.chapterParseFailed()) {
            return failureResult(
                    "",
                    "CHAPTER_PARSE_FAILED",
                    state.chapterParseOutput().errorMessage(),
                    state,
                    List.of()
            );
        }
        if (state.yamlValid()) {
            if (state.checks().contains("yaml_repair")) {
                state.warn("YAML was repaired after initial validation failure.");
            }
            return successResult(state);
        }
        if (state.validationResult() != null && !state.validationResult().valid()) {
            state.warn("YAML validation failed after one repair attempt.");
            return failureResult(
                    state.yaml(),
                    "YAML_VALIDATION_FAILED",
                    "YAML validation failed after one repair attempt.",
                    state,
                    validationIssues(state.validationResult())
            );
        }
        return failureResult("", "AGENT_INCOMPLETE", "Agent stopped before producing valid YAML.", state, List.of());
    }

    private boolean canCallTool(AgentState state) {
        return state.toolCalls() < maxToolCalls;
    }

    private AgentResult successResult(AgentState state) {
        return new AgentResult(
                state.yaml(),
                AgentResult.QualityReport.success(state.checks(), validationIssues(state.validationResult())),
                state.trace(),
                state.warnings()
        );
    }

    private AgentResult stepLimitResult(AgentState state, AgentAction nextAction) {
        String message = "Agent stopped before %s because the maximum tool call limit of %d was reached."
                .formatted(nextAction.traceName(), maxToolCalls);
        state.warn(AGENT_STEP_LIMIT_EXCEEDED);
        state.recordTrace("step_limit", message);
        return failureResult("", AGENT_STEP_LIMIT_EXCEEDED, message, state, List.of());
    }

    private AgentResult failureResult(
            String yaml,
            String errorCode,
            String message,
            AgentState state,
            List<AgentResult.ValidationIssue> validationIssues
    ) {
        return new AgentResult(
                yaml,
                AgentResult.QualityReport.failure(errorCode, message, state.checks(), validationIssues),
                state.trace(),
                state.warnings()
        );
    }

    private String validationSummary(YamlSchemaValidationResult validationResult) {
        if (validationResult.valid()) {
            return "YAML validation passed.";
        }
        return "YAML validation failed with %d error(s).".formatted(validationResult.errors().size());
    }

    private List<AgentResult.ValidationIssue> validationIssues(YamlSchemaValidationResult validationResult) {
        if (validationResult == null) {
            return List.of();
        }
        return validationResult.errors().stream()
                .map(AgentResult.ValidationIssue::from)
                .toList();
    }
}
