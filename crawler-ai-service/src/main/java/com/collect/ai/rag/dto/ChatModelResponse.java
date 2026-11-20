package com.collect.ai.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ChatModelResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private int contextLength;
    private boolean apiKeyConfigured;
}
