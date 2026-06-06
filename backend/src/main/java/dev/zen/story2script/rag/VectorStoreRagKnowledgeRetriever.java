package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * VectorStore-backed RAG retriever.
 */
@Primary
@Component
@ConditionalOnBean(VectorStore.class)
@ConditionalOnProperty(prefix = "story2script.rag", name = "vector-store-enabled", havingValue = "true")
public class VectorStoreRagKnowledgeRetriever implements RagKnowledgeRetriever {

    private final VectorStore vectorStore;
    private final RagProperties properties;

    public VectorStoreRagKnowledgeRetriever(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public List<Document> retrieve(RagSearchRequest request) {
        String filter = "target_format == '%s'".formatted(escape(request.targetFormat()));
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(request.query())
                .topK(Math.max(request.topK() <= 0 ? properties.getTopK() : request.topK(), 8))
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression(filter)
                .build()).stream()
                .filter(document -> promptSlotMatches(document, request.promptSlot()))
                .limit(request.topK() <= 0 ? properties.getTopK() : request.topK())
                .toList();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    private boolean promptSlotMatches(Document document, String promptSlot) {
        if (promptSlot == null || promptSlot.isBlank()) {
            return true;
        }
        Object value = document.getMetadata().get("prompt_slots");
        return value != null && String.valueOf(value).contains(promptSlot);
    }
}
