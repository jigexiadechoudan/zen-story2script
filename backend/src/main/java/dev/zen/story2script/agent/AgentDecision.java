package dev.zen.story2script.agent;

/**
 * Planner output for one ReAct loop iteration.
 */
record AgentDecision(AgentAction action, String summary) {

    AgentDecision {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        summary = summary == null ? "" : summary;
    }
}
