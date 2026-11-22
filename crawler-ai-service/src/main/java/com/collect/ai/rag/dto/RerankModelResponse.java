package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class RerankModelResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String rerankProvider;
    private String rerankBaseUrl;
    private String rerankModel;
    private boolean apiKeyConfigured;
}
