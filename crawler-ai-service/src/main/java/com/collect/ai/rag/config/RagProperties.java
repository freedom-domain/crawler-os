package com.collect.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class RagProperties {

    private volatile Rag rag = new Rag();

    public void replace(Rag replacement) {
        this.rag = replacement;
    }

    @Data
    public static class Rag {
        private boolean enabled;
        private String sourceIndex = "spider_content";
        private String embeddingProvider = "ollama";
        private String embeddingBaseUrl = "http://192.168.31.2:11434";
        private String chatProvider = "openai";
        private String chatBaseUrl = "http://192.168.31.2:8900/v1";
        private String apiKey = "";
        private String embeddingModel = "bge-m3";
        private String chatModel = "Qwen3.8-27B";
        private int contextLength = 8192;
        private int dimensions = 1024;
        private int topK = 5;
        private int chunkSize = 1500;
        private int chunkOverlap = 200;
        private String vectorIndex;
        private String vectorIndexPrefix = "spider_content_vector";
        private String vectorFieldPrefix = "embedding";
    }

    public String resolveVectorIndex() {
        Rag current = rag;
        if (current.vectorIndex != null && !current.vectorIndex.isBlank()) {
            return current.vectorIndex.trim();
        }
        return current.vectorIndexPrefix + "_" + sanitize(current.embeddingModel);
    }

    public String resolveVectorField() {
        Rag current = rag;
        return current.vectorFieldPrefix + "_" + sanitize(current.embeddingModel);
    }

    public int resolveDimensions() {
        Rag current = rag;
        return current.dimensions;
    }

    private static String sanitize(String value) {
        return value == null ? "unknown"
                : value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
