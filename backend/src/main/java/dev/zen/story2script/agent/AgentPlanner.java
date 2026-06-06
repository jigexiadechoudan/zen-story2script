package dev.zen.story2script.agent;

/**
 * Chooses the next action from the current observations.
 */
interface AgentPlanner {

    AgentDecision decide(AgentState state);
}
