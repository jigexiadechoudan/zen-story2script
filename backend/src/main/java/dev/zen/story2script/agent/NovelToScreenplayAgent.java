package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.schema.YamlSchemaValidator;
import dev.zen.story2script.tools.ChapterParseTool;
import dev.zen.story2script.tools.ScreenplayYamlWriteTool;
import dev.zen.story2script.tools.ScreenplayQualityReviewTool;
import dev.zen.story2script.tools.SourceCoverageAuditTool;
import dev.zen.story2script.tools.ToolLlmClient;
import dev.zen.story2script.tools.YamlRepairTool;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ReAct loop for converting a novel excerpt into screenplay YAML.
 */
@Service
public class NovelToScreenplayAgent {

    private final ChapterParseTool chapterParseTool;
    private final ScreenplayYamlWriteTool screenplayYamlWriteTool;
    private final SourceCoverageAuditTool sourceCoverageAuditTool;
    private final ScreenplayQualityReviewTool screenplayQualityReviewTool;
    private final YamlSchemaValidator yamlSchemaValidator;
    private final YamlRepairTool yamlRepairTool;
    private final ToolLlmClient toolLlmClient;
    private final boolean devFallback;
    private final List<Advisor> retrievalAdvisors;
    private final boolean autonomousEnabled;
    private final Duration autonomousTimeout;
    private final int maxInputChars;

    @Autowired
    public NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            SourceCoverageAuditTool sourceCoverageAuditTool,
            ScreenplayQualityReviewTool screenplayQualityReviewTool,
            YamlRepairTool yamlRepairTool,
            ToolLlmClient toolLlmClient,
            org.springframework.beans.factory.ObjectProvider<Advisor> advisors,
            @Value("${story2script.agent.autonomous-enabled:true}") boolean autonomousEnabled,
            @Value("${story2script.agent.autonomous-timeout:45s}") Duration autonomousTimeout,
            @Value("${story2script.agent.max-input-chars:12000}") int maxInputChars
    ) {
        this(
                chapterParseTool,
                screenplayYamlWriteTool,
                sourceCoverageAuditTool,
                screenplayQualityReviewTool,
                new YamlSchemaValidator(),
                yamlRepairTool,
                toolLlmClient,
                toolLlmClient.devFallback(),
                advisors.orderedStream().toList(),
                autonomousEnabled,
                autonomousTimeout,
                maxInputChars
        );
    }

    NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlSchemaValidator yamlSchemaValidator,
            YamlRepairTool yamlRepairTool,
            ToolLlmClient toolLlmClient,
            boolean devFallback
    ) {
        this(
                chapterParseTool,
                screenplayYamlWriteTool,
                new SourceCoverageAuditTool(),
                new ScreenplayQualityReviewTool(),
                yamlSchemaValidator,
                yamlRepairTool,
                toolLlmClient,
                devFallback,
                List.of(),
                false,
                Duration.ofSeconds(45),
                12_000
        );
    }

    NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            SourceCoverageAuditTool sourceCoverageAuditTool,
            ScreenplayQualityReviewTool screenplayQualityReviewTool,
            YamlSchemaValidator yamlSchemaValidator,
            YamlRepairTool yamlRepairTool,
            ToolLlmClient toolLlmClient,
            boolean devFallback,
            List<Advisor> retrievalAdvisors,
            boolean autonomousEnabled,
            Duration autonomousTimeout,
            int maxInputChars
    ) {
        if (autonomousTimeout == null || autonomousTimeout.isNegative() || autonomousTimeout.isZero()) {
            throw new IllegalArgumentException("autonomousTimeout must be positive");
        }
        if (maxInputChars < 1000) {
            throw new IllegalArgumentException("maxInputChars must be at least 1000");
        }
        this.chapterParseTool = chapterParseTool;
        this.screenplayYamlWriteTool = screenplayYamlWriteTool;
        this.sourceCoverageAuditTool = sourceCoverageAuditTool;
        this.screenplayQualityReviewTool = screenplayQualityReviewTool;
        this.yamlSchemaValidator = yamlSchemaValidator;
        this.yamlRepairTool = yamlRepairTool;
        this.toolLlmClient = toolLlmClient;
        this.devFallback = devFallback;
        this.retrievalAdvisors = List.copyOf(retrievalAdvisors == null ? List.of() : retrievalAdvisors);
        this.autonomousEnabled = autonomousEnabled;
        this.autonomousTimeout = autonomousTimeout;
        this.maxInputChars = maxInputChars;
    }

    public AgentResult convert(AgentContext context) {
        return convert(context, AgentProgressListener.NOOP);
    }

    public AgentResult convert(AgentContext context, AgentProgressListener progressListener) {
        if (shouldUseAutonomousReAct(context)) {
            return convertAutonomouslyWithFallback(context, progressListener);
        }

        AgentState state = new AgentState(context);
        AgentProgressListener listener = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        if (devFallback) {
            enterDevFallback(state);
        }
        return convertFast(state, listener);
    }

    private boolean shouldUseAutonomousReAct(AgentContext context) {
        return canUseAutonomousReActDependencies()
                && !context.fastMode();
    }

    private boolean canUseAutonomousReActDependencies() {
        return autonomousEnabled
                && !devFallback;
    }

    private AgentResult convertAutonomouslyWithFallback(AgentContext context, AgentProgressListener progressListener) {
        AgentResult autonomousResult = convertAutonomously(context, progressListener);
        if (autonomousResult.qualityReport().success()) {
            return autonomousResult;
        }
        return convertFastAfterAutonomousFailure(
                context,
                progressListener,
                "AUTONOMOUS_REACT_UNUSABLE",
                autonomousResult.qualityReport().message()
        );
    }

    private String runBoundedThinkCall(String systemPrompt, String userPrompt) {
        CompletableFuture<String> think = CompletableFuture.supplyAsync(
                () -> toolLlmClient.generate(systemPrompt, userPrompt, retrievalAdvisors)
        );
        try {
            return think.get(autonomousTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            think.cancel(true);
            throw new ReactThinkTimeoutException("ReAct think timed out after %s.".formatted(autonomousTimeout));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ReactThinkTimeoutException("ReAct think was interrupted: " + nullToEmpty(ex.getMessage()));
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new ReactThinkTimeoutException("ReAct think failed: " + nullToEmpty(ex.getMessage()));
        }
    }

    private String finishDecisionAfterThinkFailure(AgentState state, ReactPhase phase, RuntimeException ex) {
        String message = "%s; continuing fixed conversion chain without optional %s tool."
                .formatted(nullToEmpty(ex.getMessage()), phase.displayName());
        state.warn(message);
        state.addCheck("react_think_timeout_" + phase.checkName());
        return """
                {"thought":"bounded ReAct think failed within timeout","action":"finish","repair_hint":""}
                """;
    }

    private AgentResult convertFastAfterAutonomousFailure(
            AgentContext context,
            AgentProgressListener progressListener,
            String warning,
            String reason
    ) {
        AgentState fallbackState = new AgentState(context);
        fallbackState.setTraceMode("react_fallback_fast");
        fallbackState.warn(warning);
        fallbackState.warn("Autonomous ReAct could not produce a usable result; fell back to fast mode: "
                + nullToEmpty(reason));
        fallbackState.recordTrace("autonomous_fallback", warning + "; running fast mode.");
        AgentResult fastResult = convertFast(fallbackState, progressListener);
        List<String> warnings = new java.util.ArrayList<>(fastResult.warnings());
        warnings.add(warning);
        return new AgentResult(
                fastResult.yaml(),
                fastResult.qualityReport(),
                fastResult.agentTrace(),
                List.copyOf(warnings)
        );
    }

    private AgentResult convertAutonomously(AgentContext context, AgentProgressListener progressListener) {
        AgentProgressListener listener = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        AgentState state = new AgentState(context);
        state.setTraceMode("react");
        state.addCheck("bounded_react");
        state.addCheck("autonomousTimeout=%s".formatted(autonomousTimeout));
        state.addCheck("maxInputChars=%d".formatted(maxInputChars));
        if (!retrievalAdvisors.isEmpty()) {
            state.addCheck("rag_advisor_attached");
        }

        try {
            parseChapters(state);
            state.lastTraceStep().ifPresent(listener::onStep);
            if (state.chapterParseFailed()) {
                return finish(state);
            }

            String preObservation = runReactDecision(
                    state,
                    listener,
                    ReactPhase.BEFORE_WRITE,
                    ""
            );

            writeFastYaml(state, retrievalAdvisors, preObservation);
            state.lastTraceStep().ifPresent(listener::onStep);

            validateYaml(state);
            state.lastTraceStep().ifPresent(listener::onStep);

            String postObservation = runReactDecision(
                    state,
                    listener,
                    ReactPhase.AFTER_WRITE,
                    preObservation
            );
            if (!state.yamlValid() && state.canRepairYaml()) {
                repairYaml(state, retrievalAdvisors, postObservation);
                state.lastTraceStep().ifPresent(listener::onStep);
                validateYaml(state);
                state.lastTraceStep().ifPresent(listener::onStep);
            } else if (shouldRepairAfterReactObservation(postObservation) && state.canRepairDraft()) {
                repairYaml(state, retrievalAdvisors, postObservation);
                state.lastTraceStep().ifPresent(listener::onStep);
                validateYaml(state);
                state.lastTraceStep().ifPresent(listener::onStep);
            }
            return finish(state);
        } catch (IllegalArgumentException ex) {
            state.warn(ex.getMessage());
            return failureResult("", "AGENT_INPUT_INVALID", ex.getMessage(), state, List.of());
        } catch (RuntimeException ex) {
            String message = "Bounded ReAct conversion failed: " + ex.getMessage();
            state.warn(message);
            return failureResult("", "AGENT_TOOL_FAILED", message, state, List.of());
        }
    }

    private String runReactDecision(
            AgentState state,
            AgentProgressListener listener,
            ReactPhase phase,
            String previousObservation
    ) {
        String decision = boundedReactThink(state, phase, previousObservation);
        state.recordTrace("react_think", decisionSummary(phase, decision));
        state.lastTraceStep().ifPresent(listener::onStep);
        state.addCheck("react_think_" + phase.checkName());

        ReactAction action = ReactAction.fromDecision(decision, phase);
        return switch (action) {
            case SOURCE_COVERAGE_AUDIT -> runSourceCoverageAudit(state, listener);
            case SCREENPLAY_QUALITY_REVIEW -> runScreenplayQualityReview(state, listener);
            case FINISH -> {
                state.recordTrace("react_finish", "Model decided no optional audit tool was needed for %s."
                        .formatted(phase.displayName()));
                state.lastTraceStep().ifPresent(listener::onStep);
                yield "";
            }
        };
    }

    private String boundedReactThink(AgentState state, ReactPhase phase, String previousObservation) {
        try {
            return runBoundedThinkCall(
                    boundedReactSystemPrompt(phase),
                    boundedReactUserPrompt(state, phase, previousObservation)
            );
        } catch (ReactThinkTimeoutException ex) {
            return finishDecisionAfterThinkFailure(state, phase, ex);
        }
    }

    private String runSourceCoverageAudit(AgentState state, AgentProgressListener listener) {
        state.recordToolCall("source_coverage_audit", "Audited source chapter coverage.");
        SourceCoverageAuditTool.SourceCoverageAuditOutput output = sourceCoverageAuditTool.audit(
                new SourceCoverageAuditTool.SourceCoverageAuditInput(
                        state.context().title(),
                        state.chapterParseOutput().chapters(),
                        state.yaml()
                )
        );
        state.addCheck("source_coverage_audit");
        String observation = formatObservation("source_coverage_audit", output.pass(), output.summary(), output.findings());
        state.replaceLastTraceSummary(observation);
        state.lastTraceStep().ifPresent(listener::onStep);
        return observation;
    }

    private String runScreenplayQualityReview(AgentState state, AgentProgressListener listener) {
        if (state.yaml().isBlank()) {
            return runSourceCoverageAudit(state, listener);
        }
        state.recordToolCall("screenplay_quality_review", "Reviewed draft performability.");
        ScreenplayQualityReviewTool.ScreenplayQualityReviewOutput output = screenplayQualityReviewTool.review(
                new ScreenplayQualityReviewTool.ScreenplayQualityReviewInput(state.context().title(), state.yaml())
        );
        state.addCheck("screenplay_quality_review");
        String observation = formatObservation("screenplay_quality_review", output.pass(), output.summary(), output.findings());
        state.replaceLastTraceSummary(observation);
        state.lastTraceStep().ifPresent(listener::onStep);
        return observation;
    }

    private boolean shouldRepairAfterReactObservation(String observation) {
        return observation != null
                && !observation.isBlank()
                && observation.contains("pass=false");
    }

    private String formatObservation(String tool, boolean pass, String summary, List<String> findings) {
        return "%s pass=%s; %s; findings=%s".formatted(
                tool,
                pass,
                nullToEmpty(summary),
                findings == null ? List.of() : findings
        );
    }

    private String decisionSummary(ReactPhase phase, String decision) {
        return "ReAct %s decision: %s".formatted(phase.displayName(), compact(decision, 180));
    }

    private String boundedReactSystemPrompt(ReactPhase phase) {
        return """
                You are a bounded ReAct controller for a novel-to-screenplay converter.
                The main conversion chain is fixed by the application. Your only job is to think, then choose
                exactly one optional action for the %s phase.

                Available actions:
                - source_coverage_audit: check whether the source chapters are likely covered.
                - screenplay_quality_review: check whether the draft is performable and scene-level.
                - finish: skip optional tool use when no tool is useful.

                Return one compact JSON object only:
                {"thought":"short reason","action":"source_coverage_audit|screenplay_quality_review|finish","repair_hint":"short optional hint"}
                Do not return Markdown fences or additional commentary.
                """.formatted(phase.displayName());
    }

    private String boundedReactUserPrompt(AgentState state, ReactPhase phase, String previousObservation) {
        return """
                Phase: %s
                Title: %s
                Author: %s
                Language: %s
                Target format: %s
                Target duration: %s
                Style hint: %s
                Parsed chapter count: %d
                Previous observation: %s
                Draft YAML available: %s
                Draft validation: %s

                Parsed chapters:
                %s
                """.formatted(
                phase.displayName(),
                state.context().title(),
                nullToEmpty(state.context().originalAuthor()),
                nullToEmpty(state.context().language()),
                state.context().targetFormat(),
                nullToEmpty(state.context().targetDuration()),
                nullToEmpty(state.context().styleHint()),
                state.chapterParseOutput() == null ? 0 : state.chapterParseOutput().chapters().size(),
                nullToEmpty(previousObservation),
                !state.yaml().isBlank(),
                state.validationResult() == null ? "not_validated" : state.validationResult().valid(),
                compact(formatChapters(state.chapterParseOutput().chapters()), maxInputChars)
        );
    }

    private String compact(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return nullToEmpty(value);
        }
        return value.substring(0, limit) + "\n[TRUNCATED]";
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

    private AgentResult convertFast(AgentState state, AgentProgressListener listener) {
        try {
            if (!state.hasTraceMode()) {
                state.setTraceMode("fast");
            }
            state.addCheck("fast_mode");
            parseChapters(state);
            state.lastTraceStep().ifPresent(listener::onStep);
            if (state.chapterParseFailed()) {
                return finish(state);
            }

            writeFastYaml(state);
            state.lastTraceStep().ifPresent(listener::onStep);

            validateYaml(state);
            state.lastTraceStep().ifPresent(listener::onStep);
            if (!state.yamlValid() && state.canRepairYaml()) {
                repairYaml(state);
                state.lastTraceStep().ifPresent(listener::onStep);
                validateYaml(state);
                state.lastTraceStep().ifPresent(listener::onStep);
            }
            return finish(state);
        } catch (IllegalArgumentException ex) {
            state.warn(ex.getMessage());
            return failureResult("", "AGENT_INPUT_INVALID", ex.getMessage(), state, List.of());
        } catch (RuntimeException ex) {
            String message = "Agent fast conversion failed: " + ex.getMessage();
            state.warn(message);
            return failureResult("", "AGENT_TOOL_FAILED", message, state, List.of());
        }
    }

    private void parseChapters(AgentState state) {
        state.recordToolCall(AgentAction.PARSE_CHAPTERS.traceName(), "Parsed source text into chapters.");
        ChapterParseTool.ChapterParseOutput output = chapterParseTool.parse(
                new ChapterParseTool.ChapterParseInput(state.context().sourceText())
        );
        state.observeChapterParse(output);
        state.replaceLastTraceSummary(chapterParseSummary(output));
        if (!output.valid()) {
            state.warn(output.errorMessage());
        }
    }

    private void writeFastYaml(AgentState state) {
        writeFastYaml(state, List.of(), "");
    }

    private void writeFastYaml(AgentState state, List<Advisor> advisors, String reactObservation) {
        state.recordToolCall(AgentAction.WRITE_YAML.traceName(), "Generated compact screenplay YAML draft in fast mode.");
        ScreenplayYamlWriteTool.ScreenplayYamlWriteOutput output = screenplayYamlWriteTool.writeFast(
                new ScreenplayYamlWriteTool.FastScreenplayYamlWriteInput(
                        state.context().title(),
                        state.context().originalAuthor(),
                        state.context().language(),
                        state.context().targetFormat(),
                        state.context().targetDuration(),
                        appendReactObservation(state.context().styleHint(), reactObservation),
                        state.chapterParseOutput().chapters()
                ),
                advisors
        );
        state.observeYamlWrite(output.yaml());
        if (devFallback) {
            state.replaceLastTraceSummary("已生成快速模式示例 YAML：使用请求元数据构造 dev fallback 演示输出。");
        }
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
        repairYaml(state, List.of(), "", "YAML validation failed; attempting one repair.");
    }

    private void repairYaml(AgentState state, List<Advisor> advisors, String guidance) {
        repairYaml(state, advisors, guidance, repairWarning(state, guidance));
    }

    private void repairYaml(AgentState state, List<Advisor> advisors, String guidance, String warning) {
        state.warn(warning);
        state.recordToolCall(AgentAction.REPAIR_YAML.traceName(), "Repaired screenplay YAML after validation failure.");
        YamlRepairTool.YamlRepairOutput output = yamlRepairTool.repair(
                new YamlRepairTool.YamlRepairInput(
                        state.yaml(),
                        state.validationResult() == null ? List.of() : state.validationResult().errors(),
                        guidance
                ),
                advisors
        );
        state.observeYamlRepair(output.yaml());
    }

    private String repairWarning(AgentState state, String guidance) {
        if (state.validationResult() != null && !state.validationResult().valid()) {
            return "YAML validation failed; attempting one repair.";
        }
        if (guidance != null && !guidance.isBlank()) {
            return "Bounded ReAct review found draft issues; attempting one guided repair.";
        }
        return "Attempting one YAML repair.";
    }

    private String appendReactObservation(String styleHint, String reactObservation) {
        if (reactObservation == null || reactObservation.isBlank()) {
            return styleHint;
        }
        return (nullToEmpty(styleHint) + "\nBounded ReAct observation: " + reactObservation).trim();
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
            if (devFallback) {
                state.recordTrace("dev_fallback", "未调用真实大模型。");
                recordDevFallbackQuality(state);
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

    private void enterDevFallback(AgentState state) {
        state.warn("当前使用 dev fallback 演示输出。");
        state.warn("未调用真实大模型。");
        state.warn("配置 application-local.yml 后可启用真实模型。");
        state.recordTrace("dev_fallback", "已进入 dev fallback：当前未配置真实 ChatModel。");
    }

    private String chapterParseSummary(ChapterParseTool.ChapterParseOutput output) {
        if (devFallback) {
            return "已解析章节：%d 章；valid=%s。".formatted(output.chapters().size(), output.valid());
        }
        return "Parsed %d chapters; valid=%s.".formatted(output.chapters().size(), output.valid());
    }

    private void recordDevFallbackQuality(AgentState state) {
        int chapterCount = state.chapterParseOutput() == null ? 0 : state.chapterParseOutput().chapters().size();
        int characterCount = countYamlListItems(state.yaml(), "characters");
        int sceneCount = countYamlListItems(state.yaml(), "scenes");
        state.addCheck("chapterCount=%d".formatted(chapterCount));
        state.addCheck("characterCount=%d".formatted(characterCount));
        state.addCheck("sceneCount=%d".formatted(sceneCount));
        state.addCheck("reactSteps=%d".formatted(state.agentStepCount()));
        state.addCheck("repaired=%s".formatted(state.checks().contains("yaml_repair")));
    }

    private int countYamlListItems(String yaml, String fieldName) {
        String[] lines = yaml.split("\\R");
        boolean inside = false;
        int parentIndent = -1;
        int count = 0;
        for (String line : lines) {
            int indent = indentation(line);
            String trimmed = line.trim();
            if (trimmed.equals(fieldName + ":")) {
                inside = true;
                parentIndent = indent;
                continue;
            }
            if (inside && !trimmed.isBlank() && indent <= parentIndent) {
                break;
            }
            if (inside && indent == parentIndent + 2 && trimmed.startsWith("- ")) {
                count++;
            }
        }
        return count;
    }

    private int indentation(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum ReactPhase {
        BEFORE_WRITE("before_write", "before write"),
        AFTER_WRITE("after_write", "after write");

        private final String checkName;
        private final String displayName;

        ReactPhase(String checkName, String displayName) {
            this.checkName = checkName;
            this.displayName = displayName;
        }

        private String checkName() {
            return checkName;
        }

        private String displayName() {
            return displayName;
        }
    }

    private enum ReactAction {
        SOURCE_COVERAGE_AUDIT,
        SCREENPLAY_QUALITY_REVIEW,
        FINISH;

        private static ReactAction fromDecision(String decision, ReactPhase phase) {
            String normalized = decision == null ? "" : decision.toLowerCase();
            if (normalized.contains("screenplay_quality_review") && phase == ReactPhase.AFTER_WRITE) {
                return SCREENPLAY_QUALITY_REVIEW;
            }
            if (normalized.contains("source_coverage_audit")) {
                return SOURCE_COVERAGE_AUDIT;
            }
            if (phase == ReactPhase.BEFORE_WRITE && !normalized.contains("finish")) {
                return SOURCE_COVERAGE_AUDIT;
            }
            return FINISH;
        }
    }

    private AgentResult successResult(AgentState state) {
        return new AgentResult(
                state.yaml(),
                AgentResult.QualityReport.success(state.checks(), validationIssues(state.validationResult())),
                state.trace(),
                state.warnings()
        );
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

    private static final class ReactThinkTimeoutException extends RuntimeException {
        private ReactThinkTimeoutException(String message) {
            super(message);
        }
    }
}
