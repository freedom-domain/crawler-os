package com.collect.ai.rag.dto;

import lombok.Data;

@Data
public class ChatModelResponse {
    private Long id;
    private String name;
    private String chatProvider;
    private String chatBaseUrl;
    private String chatModel;
    private boolean apiKeyConfigured;
}
