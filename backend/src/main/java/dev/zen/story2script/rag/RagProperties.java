package dev.zen.story2script.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "story2script.rag")
public class RagProperties {

    private boolean vectorStoreEnabled;
    private boolean syncOnStartup;
    private int topK = 3;
    private double similarityThreshold = 0.0;

    public boolean isVectorStoreEnabled() {
        return vectorStoreEnabled;
    }

    public void setVectorStoreEnabled(boolean vectorStoreEnabled) {
        this.vectorStoreEnabled = vectorStoreEnabled;
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
