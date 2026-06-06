package dev.zen.story2script.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RagKnowledgeTests {

    @Test
    void staticReaderLoadsChunkedKnowledgeDocuments() {
        StaticRagDocumentReader reader = new StaticRagDocumentReader();

        List<Document> documents = reader.get();

        assertThat(documents).hasSize(21);
        assertThat(documents)
                .extracting(document -> document.getMetadata().get("chunk_id"))
                .contains(
                        "short_drama.intent",
                        "screenplay.action_rules",
                        "scene_outline.scene_unit"
                );
        assertThat(documents)
                .allSatisfy(document -> assertThat(document.getText())
                        .contains("Summary:")
                        .contains("Guidance:")
                        .contains("Prompt notes:")
                        .contains("Checks:"));
    }

    @Test
    void inMemoryRetrieverFiltersByTargetAndPromptSlot() {
        RagKnowledgeRetriever retriever = new InMemoryRagKnowledgeRetriever(new StaticRagDocumentReader());

        List<Document> documents = retriever.retrieve(new RagSearchRequest(
                "short_drama",
                "scene_planning",
                "短剧 场景 钩子 悬念",
                5
        ));

        assertThat(documents).isNotEmpty();
        assertThat(documents)
                .allSatisfy(document -> assertThat(document.getMetadata().get("target_format")).isEqualTo("short_drama"));
        assertThat(documents)
                .extracting(document -> document.getMetadata().get("chunk_id"))
                .contains("short_drama.intent", "short_drama.episode_structure");
    }

    @Test
    void knowledgeServiceFormatsPromptContext() {
        RagKnowledgeService service = new RagKnowledgeService(
                new InMemoryRagKnowledgeRetriever(new StaticRagDocumentReader()),
                2
        );

        String context = service.promptContext("screenplay", "yaml_writing", "动作 对白 可拍摄");

        assertThat(context)
                .contains("Retrieved adaptation knowledge")
                .contains("screenplay")
                .contains("Summary:")
                .contains("Guidance:");
    }

    @Test
    void vectorStoreRetrieverUsesVectorStoreAndFiltersPromptSlot() {
        CapturingVectorStore vectorStore = new CapturingVectorStore(List.of(
                new Document("1", "YAML writing", java.util.Map.of(
                        "chunk_id", "short_drama.output_contract",
                        "target_format", "short_drama",
                        "prompt_slots", "`yaml_writing`"
                )),
                new Document("2", "Planning", java.util.Map.of(
                        "chunk_id", "short_drama.intent",
                        "target_format", "short_drama",
                        "prompt_slots", "`scene_planning`"
                ))
        ));
        RagProperties properties = new RagProperties();
        properties.setTopK(3);
        VectorStoreRagKnowledgeRetriever retriever = new VectorStoreRagKnowledgeRetriever(vectorStore, properties);

        List<Document> documents = retriever.retrieve(new RagSearchRequest(
                "short_drama",
                "yaml_writing",
                "短剧 YAML",
                3
        ));

        assertThat(documents).singleElement()
                .extracting(document -> document.getMetadata().get("chunk_id"))
                .isEqualTo("short_drama.output_contract");
        assertThat(vectorStore.requests()).singleElement()
                .satisfies(request -> {
                    assertThat(request.getQuery()).isEqualTo("短剧 YAML");
                    assertThat(request.getFilterExpression()).isNotNull();
                });
    }

    @Test
    void initializerReplacesStableChunkDocuments() {
        CapturingVectorStore vectorStore = new CapturingVectorStore(List.of());
        RagVectorStoreInitializer initializer = new RagVectorStoreInitializer(vectorStore, new StaticRagDocumentReader());

        initializer.run(null);

        assertThat(vectorStore.deletedIds()).hasSize(21);
        assertThat(vectorStore.addedDocuments()).hasSize(21);
        assertThat(vectorStore.addedDocuments())
                .extracting(Document::getId)
                .contains("short_drama.intent:0.2.0");
    }

    private static final class CapturingVectorStore implements VectorStore {

        private final List<Document> searchResults;
        private final List<SearchRequest> requests = new ArrayList<>();
        private final List<Document> addedDocuments = new ArrayList<>();
        private final List<String> deletedIds = new ArrayList<>();

        private CapturingVectorStore(List<Document> searchResults) {
            this.searchResults = searchResults;
        }

        @Override
        public void add(List<Document> documents) {
            addedDocuments.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
            deletedIds.addAll(idList);
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            requests.add(request);
            return searchResults;
        }

        @Override
        public <T> Optional<T> getNativeClient() {
            return Optional.empty();
        }

        private List<SearchRequest> requests() {
            return requests;
        }

        private List<Document> addedDocuments() {
            return addedDocuments;
        }

        private List<String> deletedIds() {
            return deletedIds;
        }
    }
}
