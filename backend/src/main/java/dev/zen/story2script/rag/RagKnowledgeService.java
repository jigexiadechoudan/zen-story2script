package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Formats retrieved adaptation knowledge for prompt injection.
 */
@Service
public class RagKnowledgeService {

    public static final RagKnowledgeService DISABLED = new RagKnowledgeService(new NoOpRagKnowledgeRetriever(), 0);

    private final RagKnowledgeRetriever retriever;
    private final int defaultTopK;

    @Autowired
    public RagKnowledgeService(RagKnowledgeRetriever retriever) {
        this(retriever, 3);
    }

    RagKnowledgeService(RagKnowledgeRetriever retriever, int defaultTopK) {
        this.retriever = retriever;
        this.defaultTopK = defaultTopK;
    }

    public String promptContext(String targetFormat, String promptSlot, String query) {
        List<Document> documents = retriever.retrieve(new RagSearchRequest(
                targetFormat,
                promptSlot,
                query,
                defaultTopK
        ));
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Retrieved adaptation knowledge:\n");
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            builder.append("\n[").append(i + 1).append("] ")
                    .append(metadata(document, "chunk_id"))
                    .append(" (")
                    .append(metadata(document, "chunk_type"))
                    .append(")\n")
                    .append(document.getText())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
