package com.collect.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class RagProperties {

    private volatile Rag rag = new Rag();

    public void replace(Rag replacement) {
        Rag current = rag;
        if (replacement != null) {
            if (StringUtils.hasText(current.getSourceIndex())) {
                replacement.setSourceIndex(current.getSourceIndex());
            }
            if (StringUtils.hasText(current.getVectorIndexPrefix())) {
                replacement.setVectorIndexPrefix(current.getVectorIndexPrefix());
            }
            replacement.setVectorIndexEnvironment(current.getVectorIndexEnvironment());
        }
        this.rag = replacement;
    }

    @Data
    public static class Rag {
        private String sourceIndex = "spider_content";
        private String embeddingProvider = "ollama";
        private String embeddingBaseUrl = "http://192.168.31.2:11434";
        private String embeddingApiKey = "";
        private String chatProvider = "openai";
        private String chatBaseUrl = "http://192.168.31.2:8900/v1";
        private String apiKey = "";
        private String embeddingModel = "bge-m3";
        private String chatModel = "Qwen3.8-27B";
        private int contextLength = 8192;
        private int dimensions = 1024;
        private int topK = 5;
        /** 召回融合后每个文档（doc_id）最多保留的 chunk 数：1=仅保留代表 chunk；>1=允许同一文档多个章节进入上下文 */
        private int maxChunksPerDocument = 1;
        private String vectorIndexPrefix = "spider_content_vector";
        private String vectorIndexEnvironment = "";

        public Rag copy() {
            Rag copy = new Rag();
            copy.setSourceIndex(sourceIndex);
            copy.setEmbeddingProvider(embeddingProvider);
            copy.setEmbeddingBaseUrl(embeddingBaseUrl);
            copy.setEmbeddingApiKey(embeddingApiKey);
            copy.setChatProvider(chatProvider);
            copy.setChatBaseUrl(chatBaseUrl);
            copy.setApiKey(apiKey);
            copy.setEmbeddingModel(embeddingModel);
            copy.setChatModel(chatModel);
            copy.setContextLength(contextLength);
            copy.setDimensions(dimensions);
            copy.setTopK(topK);
            copy.setMaxChunksPerDocument(maxChunksPerDocument);
            copy.setVectorIndexPrefix(vectorIndexPrefix);
            copy.setVectorIndexEnvironment(vectorIndexEnvironment);
            return copy;
        }
    }

    public String resolveVectorIndex() {
        return resolveVectorIndex(rag.embeddingModel);
    }

    public String resolveVectorIndex(String embeddingModel) {
        Rag current = rag;
        String model = sanitize(embeddingModel);
        String environment = sanitize(current.vectorIndexEnvironment);
        String prefix = StringUtils.hasText(current.vectorIndexPrefix)
                ? current.vectorIndexPrefix.trim()
                : "spider_content_vector";
        return prefix + (model.isEmpty() ? "" : "_" + model)
                + (environment.isEmpty() ? "" : "_" + environment);
    }

    public String resolveSourceIndex() {
        Rag current = rag;
        return StringUtils.hasText(current.sourceIndex)
                ? current.sourceIndex.trim()
                : "spider_content";
    }

    public String resolveVectorField() {
        return "embedding";
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
