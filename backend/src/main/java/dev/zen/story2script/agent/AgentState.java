package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.tools.ChapterParseTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mutable observations accumulated during one ReAct run.
 */
final class AgentState {

    private final AgentContext context;
    private final List<String> checks = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<AgentResult.Step> traceSteps = new ArrayList<>();
    private String traceMode;

    private ChapterParseTool.ChapterParseOutput chapterParseOutput;
    private String yaml;
    private YamlSchemaValidationResult validationResult;
    private int repairAttempts;
    private int toolCalls;

    AgentState(AgentContext context) {
        this.context = context;
    }

    AgentContext context() {
        return context;
    }

    List<String> checks() {
        return checks;
    }

    List<String> warnings() {
        return warnings;
    }

    String yaml() {
        return yaml == null ? "" : yaml;
    }

    ChapterParseTool.ChapterParseOutput chapterParseOutput() {
        return chapterParseOutput;
    }

    YamlSchemaValidationResult validationResult() {
        return validationResult;
    }

    int toolCalls() {
        return toolCalls;
    }

    int agentStepCount() {
        return traceSteps.size();
    }

    boolean chapterParseFailed() {
        return chapterParseOutput != null && !chapterParseOutput.valid();
    }

    boolean yamlValid() {
        return validationResult != null && validationResult.valid();
    }

    boolean canRepairYaml() {
        return validationResult != null && !validationResult.valid() && repairAttempts == 0;
    }

    boolean canRepairDraft() {
        return !yaml().isBlank() && repairAttempts == 0;
    }

    void observeChapterParse(ChapterParseTool.ChapterParseOutput output) {
        chapterParseOutput = output;
        checks.add("chapter_parse");
    }

    void observeYamlWrite(String output) {
        yaml = output;
        validationResult = null;
        checks.add("yaml_write");
    }

    void observeYamlValidation(YamlSchemaValidationResult output, String checkName) {
        validationResult = output;
        checks.add(checkName);
    }

    void observeYamlRepair(String output) {
        yaml = output;
        validationResult = null;
        repairAttempts++;
        checks.add("yaml_repair");
    }

    void warn(String warning) {
        warnings.add(warning);
    }

    void addCheck(String check) {
        checks.add(check);
    }

    void setTraceMode(String traceMode) {
        if (traceMode != null && !traceMode.isBlank()) {
            this.traceMode = traceMode;
        }
    }

    boolean hasTraceMode() {
        return traceMode != null && !traceMode.isBlank();
    }

    void recordToolCall(String tool, String summary) {
        toolCalls++;
        recordTrace(tool, summary);
    }

    void recordTrace(String tool, String summary) {
        traceSteps.add(new AgentResult.Step(traceSteps.size() + 1, tool, summary));
    }

    void replaceLastTraceSummary(String summary) {
        if (traceSteps.isEmpty()) {
            return;
        }
        AgentResult.Step last = traceSteps.remove(traceSteps.size() - 1);
        traceSteps.add(new AgentResult.Step(last.index(), last.tool(), summary));
    }

    Optional<AgentResult.Step> lastTraceStep() {
        if (traceSteps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(traceSteps.get(traceSteps.size() - 1));
    }

    AgentResult.AgentTrace trace() {
        return new AgentResult.AgentTrace(traceMode == null ? "fast" : traceMode, traceSteps, toolCalls);
    }
}
