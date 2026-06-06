package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic local retriever over Spring AI Document chunks.
 *
 * <p>This keeps the RAG path usable without an embedding model. It also preserves the same Document-oriented boundary
 * a VectorStore implementation would use.</p>
 */
@Component
public class InMemoryRagKnowledgeRetriever implements RagKnowledgeRetriever {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsHan}\\p{Alnum}_]+");

    private final List<Document> documents;

    public InMemoryRagKnowledgeRetriever(StaticRagDocumentReader documentReader) {
        this.documents = documentReader.get();
    }

    @Override
    public List<Document> retrieve(RagSearchRequest request) {
        Set<String> queryTerms = terms(request.query());
        return documents.stream()
                .filter(document -> targetMatches(document, request.targetFormat()))
                .filter(document -> promptSlotMatches(document, request.promptSlot()))
                .map(document -> new ScoredDocument(document, score(document, request, queryTerms)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredDocument::score).reversed()
                        .thenComparing(scored -> metadata(scored.document(), "chunk_id")))
                .limit(request.topK())
                .map(ScoredDocument::document)
                .toList();
    }

    private int score(Document document, RagSearchRequest request, Set<String> queryTerms) {
        int score = 0;
        if (targetMatches(document, request.targetFormat())) {
            score += 20;
        }
        if (promptSlotMatches(document, request.promptSlot())) {
            score += 12;
        }
        String haystack = (document.getText() + " "
                + metadata(document, "chunk_id") + " "
                + metadata(document, "chunk_type") + " "
                + metadata(document, "retrieval_terms") + " "
                + metadata(document, "summary")).toLowerCase(Locale.ROOT);
        for (String term : queryTerms) {
            if (haystack.contains(term)) {
                score += 2;
            }
        }
        return score;
    }

    private boolean targetMatches(Document document, String targetFormat) {
        return targetFormat.isBlank() || targetFormat.equals(metadata(document, "target_format"));
    }

    private boolean promptSlotMatches(Document document, String promptSlot) {
        if (promptSlot.isBlank()) {
            return true;
        }
        return metadata(document, "prompt_slots").contains(promptSlot);
    }

    private Set<String> terms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(query.toLowerCase(Locale.ROOT))) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private record ScoredDocument(Document document, int score) {
    }
}
