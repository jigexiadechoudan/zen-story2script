package dev.zen.story2script.rag;

/**
 * Search input for adaptation knowledge retrieval.
 */
public record RagSearchRequest(
        String targetFormat,
        String promptSlot,
        String query,
        int topK
) {

    public RagSearchRequest {
        targetFormat = normalize(targetFormat);
        promptSlot = normalize(promptSlot);
        query = query == null ? "" : query.trim();
        topK = topK <= 0 ? 3 : topK;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
