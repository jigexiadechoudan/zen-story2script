package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationResult;
import dev.zen.story2script.tools.ChapterParseTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable observations accumulated during one ReAct run.
 */
final class AgentState {

    private final AgentContext context;
    private final List<String> checks = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<AgentResult.Step> traceSteps = new ArrayList<>();

    private ChapterParseTool.ChapterParseOutput chapterParseOutput;
    private String analysisJson;
    private String scenePlanJson;
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

    String analysisJson() {
        return analysisJson;
    }

    String scenePlanJson() {
        return scenePlanJson;
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

    boolean chaptersParsed() {
        return chapterParseOutput != null;
    }

    boolean chapterParseFailed() {
        return chapterParseOutput != null && !chapterParseOutput.valid();
    }

    boolean storyAnalyzed() {
        return analysisJson != null;
    }

    boolean scenesPlanned() {
        return scenePlanJson != null;
    }

    boolean yamlWritten() {
        return yaml != null;
    }

    boolean validationNeeded() {
        return yamlWritten() && validationResult == null;
    }

    boolean yamlValid() {
        return validationResult != null && validationResult.valid();
    }

    boolean canRepairYaml() {
        return validationResult != null && !validationResult.valid() && repairAttempts == 0;
    }

    void observeChapterParse(ChapterParseTool.ChapterParseOutput output) {
        chapterParseOutput = output;
        checks.add("chapter_parse");
    }

    void observeStoryAnalysis(String output) {
        analysisJson = output;
        checks.add("story_analysis");
    }

    void observeScenePlan(String output) {
        scenePlanJson = output;
        checks.add("scene_planning");
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

    AgentResult.AgentTrace trace() {
        return new AgentResult.AgentTrace("react", traceSteps, toolCalls);
    }
}
