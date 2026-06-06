package dev.zen.story2script.agent;

import dev.zen.story2script.schema.YamlSchemaValidationError;

import java.util.List;

/**
 * Public result contract returned by the agent layer.
 */
public record AgentResult(
        String yaml,
        QualityReport qualityReport,
        AgentTrace agentTrace,
        List<String> warnings
) {

    public AgentResult {
        yaml = yaml == null ? "" : yaml;
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public record QualityReport(
            boolean success,
            String errorCode,
            String message,
            List<String> checks,
            List<ValidationIssue> validationErrors
    ) {
        public QualityReport {
            errorCode = errorCode == null ? "" : errorCode;
            message = message == null ? "" : message;
            checks = List.copyOf(checks == null ? List.of() : checks);
            validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
        }

        public static QualityReport success(List<String> checks, List<ValidationIssue> validationErrors) {
            return new QualityReport(true, "", "Conversion completed.", checks, validationErrors);
        }

        public static QualityReport failure(
                String errorCode,
                String message,
                List<String> checks,
                List<ValidationIssue> validationErrors
        ) {
            return new QualityReport(false, errorCode, message, checks, validationErrors);
        }
    }

    public record AgentTrace(String mode, List<Step> steps, int toolCalls) {
        public AgentTrace {
            mode = mode == null ? "react" : mode;
            steps = List.copyOf(steps == null ? List.of() : steps);
        }
    }

    public record Step(int index, String tool, String summary) {
    }

    public record ValidationIssue(String path, String code, String message) {
        public static ValidationIssue from(YamlSchemaValidationError error) {
            return new ValidationIssue(error.path(), error.code(), error.message());
        }
    }
}
