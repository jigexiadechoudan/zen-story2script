package dev.zen.story2script.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight audit for whether the current draft appears to cover the parsed source chapters.
 */
@Component
public class SourceCoverageAuditTool {

    @Tool(name = "source_coverage_audit", description = "Audit source chapter coverage in a screenplay YAML draft.")
    public SourceCoverageAuditOutput audit(SourceCoverageAuditInput input) {
        input = ToolInputs.requireInput(input);
        List<ChapterParseTool.ParsedChapter> chapters = input.chapters();
        if (chapters.isEmpty()) {
            return new SourceCoverageAuditOutput(false, "No parsed chapters were provided.", List.of());
        }

        String yaml = ToolInputs.nullToEmpty(input.yaml());
        List<String> findings = new ArrayList<>();
        if (yaml.isBlank()) {
            findings.add("Draft YAML is not available yet; preserve all %d parsed chapters in the upcoming draft."
                    .formatted(chapters.size()));
            findings.add("Plan at least one visible event and one speakable conflict from each chapter group.");
            return new SourceCoverageAuditOutput(true, "Pre-generation source coverage planning completed.", findings);
        }

        String normalizedYaml = yaml.toLowerCase(Locale.ROOT);
        int covered = 0;
        for (ChapterParseTool.ParsedChapter chapter : chapters) {
            Set<String> terms = chapterTerms(chapter);
            boolean chapterCovered = terms.stream().anyMatch(normalizedYaml::contains)
                    || normalizedYaml.contains("chapter " + chapter.index())
                    || normalizedYaml.contains("source_chapter: \"" + chapter.heading().toLowerCase(Locale.ROOT) + "\"");
            if (chapterCovered) {
                covered++;
            } else {
                findings.add("Chapter %d may be underrepresented: %s".formatted(chapter.index(), chapter.heading()));
            }
        }

        double ratio = covered / (double) chapters.size();
        boolean pass = ratio >= 0.67;
        String summary = "Covered %d of %d parsed chapters by source references or recurring terms."
                .formatted(covered, chapters.size());
        if (pass && findings.isEmpty()) {
            findings.add("No obvious chapter coverage gap found.");
        }
        return new SourceCoverageAuditOutput(pass, summary, findings);
    }

    private Set<String> chapterTerms(ChapterParseTool.ParsedChapter chapter) {
        Set<String> terms = new LinkedHashSet<>();
        String text = (chapter.heading() + " " + chapter.content()).toLowerCase(Locale.ROOT);
        for (String raw : text.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            String term = raw.trim();
            if (term.length() >= 4) {
                terms.add(term);
            }
            if (terms.size() >= 8) {
                break;
            }
        }
        return terms;
    }

    public record SourceCoverageAuditInput(
            String title,
            List<ChapterParseTool.ParsedChapter> chapters,
            String yaml
    ) {
        public SourceCoverageAuditInput {
            chapters = List.copyOf(chapters == null ? List.of() : chapters);
        }
    }

    public record SourceCoverageAuditOutput(boolean pass, String summary, List<String> findings) {
        public SourceCoverageAuditOutput {
            findings = List.copyOf(findings == null ? List.of() : findings);
        }
    }
}
