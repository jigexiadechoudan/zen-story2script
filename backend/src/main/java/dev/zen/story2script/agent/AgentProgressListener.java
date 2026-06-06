package dev.zen.story2script.agent;

@FunctionalInterface
public interface AgentProgressListener {

    AgentProgressListener NOOP = step -> {
    };

    void onStep(AgentResult.Step step);
}
