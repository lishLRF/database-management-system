package com.dbmanager.entity;

import lombok.Data;

@Data
public class AiGenerateRequest {
    private Long connectionId;
    private String naturalLanguage;
}
