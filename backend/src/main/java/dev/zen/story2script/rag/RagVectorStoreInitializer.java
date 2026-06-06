package dev.zen.story2script.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Synchronizes static RAG knowledge documents into the configured Spring AI VectorStore.
 */
@Component
@ConditionalOnBean(VectorStore.class)
@ConditionalOnProperty(prefix = "story2script.rag", name = "sync-on-startup", havingValue = "true")
public class RagVectorStoreInitializer implements ApplicationRunner {

    private final VectorStore vectorStore;
    private final StaticRagDocumentReader documentReader;

    public RagVectorStoreInitializer(VectorStore vectorStore, StaticRagDocumentReader documentReader) {
        this.vectorStore = vectorStore;
        this.documentReader = documentReader;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> documents = documentReader.get().stream()
                .map(this::stableDocument)
                .toList();
        vectorStore.delete(documents.stream().map(Document::getId).toList());
        vectorStore.add(documents);
    }

    private Document stableDocument(Document document) {
        String id = document.getMetadata().get("chunk_id") + ":" + document.getMetadata().get("version");
        return new Document(id, document.getText(), document.getMetadata());
    }
}
