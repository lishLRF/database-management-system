package com.dbmanager.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据库连接配置实体
 */
@Data
public class DbConnection {
    private Long id;
    private String userId;
    private String name;
    private String dbType;  // postgresql/sqlite
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String passwordEncrypted;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
