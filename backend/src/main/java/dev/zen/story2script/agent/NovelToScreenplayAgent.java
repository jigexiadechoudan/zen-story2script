package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.schema.YamlSchemaValidator;
import dev.zen.story2script.tools.ChapterParseTool;
import dev.zen.story2script.tools.ScreenplayYamlWriteTool;
import dev.zen.story2script.tools.ToolLlmClient;
import dev.zen.story2script.tools.YamlRepairTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final YamlSchemaValidator yamlSchemaValidator;
    private final YamlRepairTool yamlRepairTool;
    private final boolean devFallback;
    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final List<Advisor> retrievalAdvisors;
    private final boolean autonomousEnabled;
    private final Duration autonomousTimeout;
    private final int maxInputChars;

    @Autowired
    public NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlRepairTool yamlRepairTool,
            ToolLlmClient toolLlmClient,
            ObjectProvider<ChatClient> chatClientProvider,
            ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
            ObjectProvider<Advisor> advisors,
            @Value("${story2script.agent.autonomous-enabled:true}") boolean autonomousEnabled,
            @Value("${story2script.agent.autonomous-timeout:45s}") Duration autonomousTimeout,
            @Value("${story2script.agent.max-input-chars:12000}") int maxInputChars
    ) {
        this(
                chapterParseTool,
                screenplayYamlWriteTool,
                new YamlSchemaValidator(),
                yamlRepairTool,
                toolLlmClient.devFallback(),
                chatClientProvider.getIfAvailable(),
                toolCallbackProvider.getIfAvailable(),
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
            boolean devFallback
    ) {
        this(
                chapterParseTool,
                screenplayYamlWriteTool,
                yamlSchemaValidator,
                yamlRepairTool,
                devFallback,
                null,
                null,
                List.of(),
                false,
                Duration.ofSeconds(45),
                12_000
        );
    }

    NovelToScreenplayAgent(
            ChapterParseTool chapterParseTool,
            ScreenplayYamlWriteTool screenplayYamlWriteTool,
            YamlSchemaValidator yamlSchemaValidator,
            YamlRepairTool yamlRepairTool,
            boolean devFallback,
            ChatClient chatClient,
            ToolCallbackProvider toolCallbackProvider,
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
        this.yamlSchemaValidator = yamlSchemaValidator;
        this.yamlRepairTool = yamlRepairTool;
        this.devFallback = devFallback;
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
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
                && chatClient != null
                && toolCallbackProvider != null
                && !devFallback;
    }

    private AgentResult convertAutonomouslyWithFallback(AgentContext context, AgentProgressListener progressListener) {
        CompletableFuture<AgentResult> autonomousRun = CompletableFuture.supplyAsync(
                () -> convertAutonomously(context, progressListener)
        );
        try {
            AgentResult autonomousResult = autonomousRun.get(autonomousTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (autonomousResult.qualityReport().success()) {
                return autonomousResult;
            }
            return convertFastAfterAutonomousFailure(
                    context,
                    progressListener,
                    "AUTONOMOUS_REACT_UNUSABLE",
                    autonomousResult.qualityReport().message()
            );
        } catch (TimeoutException ex) {
            autonomousRun.cancel(true);
            return convertFastAfterAutonomousFailure(
                    context,
                    progressListener,
                    "AUTONOMOUS_REACT_TIMEOUT",
                    "Autonomous ReAct timed out after %s.".formatted(autonomousTimeout)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return convertFastAfterAutonomousFailure(
                    context,
                    progressListener,
                    "AUTONOMOUS_REACT_INTERRUPTED",
                    ex.getMessage()
            );
        } catch (java.util.concurrent.ExecutionException ex) {
            return convertFastAfterAutonomousFailure(
                    context,
                    progressListener,
                    "AUTONOMOUS_REACT_FAILED",
                    ex.getMessage()
            );
        }
    }

    private AgentResult convertFastAfterAutonomousFailure(
            AgentContext context,
            AgentProgressListener progressListener,
            String warning,
            String reason
    ) {
        AgentState fallbackState = new AgentState(context);
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
        state.addCheck("llm_autonomous_tool_calling");
        state.addCheck("autonomousTimeout=%s".formatted(autonomousTimeout));
        state.addCheck("maxInputChars=%d".formatted(maxInputChars));

        try {
            state.recordTrace("llm_agent", "Started LLM-driven tool selection.");
            state.lastTraceStep().ifPresent(listener::onStep);
            String yaml = chatClient.prompt()
                    .system(autonomousSystemPrompt())
                    .user(autonomousUserPrompt(context))
                    .toolCallbacks(toolCallbackProvider)
                    .advisors(retrievalAdvisors)
                    .call()
                    .content();

            state.recordToolCall("llm_final_answer", "Received final YAML from LLM autonomous agent.");
            state.lastTraceStep().ifPresent(listener::onStep);
            state.observeYamlWrite(yaml == null ? "" : yaml.trim());

            validateYaml(state);
            state.lastTraceStep().ifPresent(listener::onStep);
            if (!state.yamlValid()) {
                return failureResult(
                        state.yaml(),
                        "YAML_VALIDATION_FAILED",
                        "LLM autonomous agent returned YAML that failed schema validation.",
                        state,
                        validationIssues(state.validationResult())
                );
            }
            return successResult(state);
        } catch (IllegalArgumentException ex) {
            state.warn(ex.getMessage());
            return failureResult("", "AGENT_INPUT_INVALID", ex.getMessage(), state, List.of());
        } catch (RuntimeException ex) {
            String message = "LLM autonomous agent execution failed: " + ex.getMessage();
            state.warn(message);
            return failureResult("", "AGENT_TOOL_FAILED", message, state, List.of());
        }
    }

    private String autonomousSystemPrompt() {
        return """
                You are an autonomous ReAct-style novel-to-screenplay agent.
                You may decide whether and when to call the available tools.

                Available tool responsibilities:
                - chapter_parse: parse and validate source chapters.
                - story_analysis: extract characters, events, and conflicts.
                - scene_planning: turn story analysis into scenes.
                - yaml_write: write the screenplay YAML draft.
                - yaml_validation: validate the screenplay YAML against the required schema.
                - yaml_repair: repair YAML after validation errors if needed.

                Use tools when they help produce a valid screenplay YAML document. Do not expose tool JSON,
                scratchpad notes, Markdown fences, or commentary in the final answer.
                The final YAML must be a playable screenplay draft: each scene needs a location, time of day,
                present characters, concrete action beats, and dialogue beats with speakers.
                All natural-language YAML values must be written in the requested output language. Keep YAML keys
                and enum values in the schema language exactly as specified.
                Do not return only a synopsis, treatment, scene outline, or abstract beat sheet.
                The final answer must be only YAML matching schema_version "1.0".
                """;
    }

    private String autonomousUserPrompt(AgentContext context) {
        return """
                Convert this novel excerpt into screenplay YAML.

                Title: %s
                Author: %s
                Language: %s
                Target format: %s
                Target duration: %s
                Style hint: %s
                Input limit: source text is capped at %d characters for autonomous ReAct.

                Source text:
                %s
                """.formatted(
                context.title(),
                nullToEmpty(context.originalAuthor()),
                nullToEmpty(context.language()),
                context.targetFormat(),
                nullToEmpty(context.targetDuration()),
                nullToEmpty(context.styleHint()),
                maxInputChars,
                limitedSourceText(context.sourceText())
        );
    }

    private String limitedSourceText(String sourceText) {
        if (sourceText.length() <= maxInputChars) {
            return sourceText;
        }
        return sourceText.substring(0, maxInputChars)
                + "\n\n[TRUNCATED: autonomous ReAct input capped at %d characters. Use fast mode or split chapters for full text.]"
                .formatted(maxInputChars);
    }

    private AgentResult convertFast(AgentState state, AgentProgressListener listener) {
        try {
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
        state.recordToolCall(AgentAction.WRITE_YAML.traceName(), "Generated compact screenplay YAML draft in fast mode.");
        ScreenplayYamlWriteTool.ScreenplayYamlWriteOutput output = screenplayYamlWriteTool.writeFast(
                new ScreenplayYamlWriteTool.FastScreenplayYamlWriteInput(
                        state.context().title(),
                        state.context().originalAuthor(),
                        state.context().language(),
                        state.context().targetFormat(),
                        state.context().targetDuration(),
                        state.context().styleHint(),
                        state.chapterParseOutput().chapters()
                )
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
}
