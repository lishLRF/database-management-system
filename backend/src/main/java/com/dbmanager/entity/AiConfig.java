package com.dbmanager.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiConfig {
    private Long id;
    private String userId;
    private String apiProvider;
    private String apiKeyEncrypted;
    private String apiBaseUrl;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
