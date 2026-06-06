package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads chunked Markdown knowledge files into Spring AI Documents.
 */
@Component
public class StaticRagDocumentReader implements DocumentReader {

    static final String RESOURCE_PATTERN =
            "classpath*:dev/zen/story2script/rag/staticresources/*_knowledge.md";

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(?<yaml>.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern CHUNK = Pattern.compile(
            "^<!--\\s*chunk:start\\s+(?<attributes>.*?)\\s*-->\\R?(?<body>.*?)^<!--\\s*chunk:end\\s*-->\\s*$",
            Pattern.DOTALL | Pattern.MULTILINE
    );
    private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern SECTION = Pattern.compile("(?m)^\\*\\*(summary|guidance|prompt_notes|checks)\\*\\*\\s*$");

    private final ResourcePatternResolver resourcePatternResolver;

    public StaticRagDocumentReader() {
        this(new PathMatchingResourcePatternResolver());
    }

    StaticRagDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<Document> get() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(RESOURCE_PATTERN);
            List<Document> documents = new ArrayList<>();
            for (Resource resource : resources) {
                documents.addAll(readResource(resource));
            }
            return List.copyOf(documents);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load RAG knowledge resources.", ex);
        }
    }

    private List<Document> readResource(Resource resource) throws IOException {
        String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> documentMetadata = frontMatter(markdown);
        documentMetadata.put("source", resource.getFilename());

        Matcher matcher = CHUNK.matcher(markdown);
        List<Document> documents = new ArrayList<>();
        while (matcher.find()) {
            Map<String, Object> metadata = new LinkedHashMap<>(documentMetadata);
            Map<String, String> attributes = attributes(matcher.group("attributes"));
            metadata.putAll(attributes);
            metadata.put("chunk_id", attributes.getOrDefault("id", ""));
            metadata.put("retrieval_terms", metadataValue(matcher.group("body"), "retrieval_terms"));
            metadata.put("prompt_slots", metadataValue(matcher.group("body"), "prompt_slots"));
            metadata.put("summary", section(matcher.group("body"), "summary"));
            documents.add(new Document(
                    content(matcher.group("body")),
                    metadata
            ));
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> frontMatter(String markdown) {
        Matcher matcher = FRONT_MATTER.matcher(markdown);
        if (!matcher.find()) {
            return new LinkedHashMap<>();
        }
        Object loaded = new Yaml().load(matcher.group("yaml"));
        if (loaded instanceof Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            map.forEach((key, value) -> metadata.put(String.valueOf(key), value));
            return metadata;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, String> attributes(String value) {
        Matcher matcher = ATTRIBUTE.matcher(value);
        Map<String, String> attributes = new LinkedHashMap<>();
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

    private String content(String chunkBody) {
        return """
                Summary:
                %s

                Guidance:
                %s

                Prompt notes:
                %s

                Checks:
                %s
                """.formatted(
                section(chunkBody, "summary"),
                section(chunkBody, "guidance"),
                section(chunkBody, "prompt_notes"),
                section(chunkBody, "checks")
        ).trim();
    }

    private String metadataValue(String chunkBody, String key) {
        Pattern pattern = Pattern.compile("(?m)^-\\s+" + Pattern.quote(key) + ":\\s*(.+)$");
        Matcher matcher = pattern.matcher(chunkBody);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String section(String chunkBody, String name) {
        Matcher matcher = SECTION.matcher(chunkBody);
        List<SectionStart> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(new SectionStart(matcher.group(1), matcher.end()));
        }
        for (int i = 0; i < starts.size(); i++) {
            SectionStart start = starts.get(i);
            if (!start.name().equals(name)) {
                continue;
            }
            int end = i + 1 < starts.size() ? starts.get(i + 1).headerStart(chunkBody) : chunkBody.length();
            return chunkBody.substring(start.contentStart(), end).trim();
        }
        return "";
    }

    private record SectionStart(String name, int contentStart) {
        int headerStart(String text) {
            int index = text.lastIndexOf("**" + name + "**", contentStart);
            return Math.max(index, 0);
        }
    }
}
