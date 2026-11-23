package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChunkStrategyResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String strategyType;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private BigDecimal similarityThreshold;
    private Integer priority;
    private Integer minDocumentLength;
    private Integer maxDocumentLength;
    private String documentTypes;
    private boolean defaultStrategy;
}
