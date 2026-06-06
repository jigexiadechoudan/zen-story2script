package dev.zen.story2script.agent;

/**
 * Deterministic planner that makes ReAct decisions from accumulated observations.
 */
final class RuleBasedAgentPlanner implements AgentPlanner {

    @Override
    public AgentDecision decide(AgentState state) {
        if (!state.chaptersParsed()) {
            return new AgentDecision(AgentAction.PARSE_CHAPTERS, "Need chapter observations before adaptation.");
        }
        if (state.chapterParseFailed()) {
            return new AgentDecision(AgentAction.FINISH, "Cannot continue because chapter parsing failed.");
        }
        if (!state.storyAnalyzed()) {
            return new AgentDecision(AgentAction.ANALYZE_STORY, "Need story analysis before planning scenes.");
        }
        if (!state.scenesPlanned()) {
            return new AgentDecision(AgentAction.PLAN_SCENES, "Need scene plan before writing YAML.");
        }
        if (!state.yamlWritten()) {
            return new AgentDecision(AgentAction.WRITE_YAML, "Need screenplay YAML draft.");
        }
        if (state.validationNeeded()) {
            return new AgentDecision(AgentAction.VALIDATE_YAML, "Need YAML validation observation.");
        }
        if (state.yamlValid()) {
            return new AgentDecision(AgentAction.FINISH, "YAML is valid; finish conversion.");
        }
        if (state.canRepairYaml()) {
            return new AgentDecision(AgentAction.REPAIR_YAML, "YAML is invalid; attempt one repair.");
        }
        return new AgentDecision(AgentAction.FINISH, "YAML remains invalid after repair limit.");
    }
}
