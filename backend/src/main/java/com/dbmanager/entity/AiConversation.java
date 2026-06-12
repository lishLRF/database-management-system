package com.dbmanager.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiConversation {
    private Long id;
    private String conversationId;
    private String userId;
    private Long connectionId;
    private String messageType; // "user" or "ai"
    private String messageContent;
    private LocalDateTime createdAt;
}
