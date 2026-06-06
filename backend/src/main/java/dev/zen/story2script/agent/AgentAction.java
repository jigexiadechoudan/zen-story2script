package dev.zen.story2script.agent;

/**
 * Bounded action set the agent planner may choose during a ReAct loop.
 */
enum AgentAction {
    PARSE_CHAPTERS("chapter_parse"),
    ANALYZE_STORY("story_analysis"),
    PLAN_SCENES("scene_planning"),
    WRITE_YAML("yaml_write"),
    VALIDATE_YAML("yaml_validation"),
    REPAIR_YAML("yaml_repair"),
    FINISH("finish");

    private final String traceName;

    AgentAction(String traceName) {
        this.traceName = traceName;
    }

    String traceName() {
        return traceName;
    }

    boolean toolCall() {
        return this != FINISH;
    }
}
