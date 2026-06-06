package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Retrieval boundary for adaptation knowledge.
 *
 * <p>The current implementation uses static Spring AI {@link Document} chunks. A later implementation can delegate to
 * a Spring AI VectorStore using the same request shape.</p>
 */
public interface RagKnowledgeRetriever {

    List<Document> retrieve(RagSearchRequest request);
}
