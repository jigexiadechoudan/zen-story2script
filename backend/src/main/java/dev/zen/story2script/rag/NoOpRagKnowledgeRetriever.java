package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;

import java.util.List;

final class NoOpRagKnowledgeRetriever implements RagKnowledgeRetriever {

    @Override
    public List<Document> retrieve(RagSearchRequest request) {
        return List.of();
    }
}
