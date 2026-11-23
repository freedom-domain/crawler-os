package com.collect.ai.rag.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChunkStrategyRequest {
    private String name;
    private String strategyType;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private BigDecimal similarityThreshold;
    private Integer priority;
    private Integer minDocumentLength;
    private Integer maxDocumentLength;
    private String documentTypes;
}
