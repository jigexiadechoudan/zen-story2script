package dev.zen.story2script.agent;

enum AgentAction {
    PARSE_CHAPTERS("chapter_parse"),
    WRITE_YAML("yaml_write"),
    VALIDATE_YAML("yaml_validation"),
    REPAIR_YAML("yaml_repair");

    private final String traceName;

    AgentAction(String traceName) {
        this.traceName = traceName;
    }

    String traceName() {
        return traceName;
    }

}
