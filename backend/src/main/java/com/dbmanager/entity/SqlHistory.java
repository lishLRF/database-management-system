package com.dbmanager.entity;

import java.time.LocalDateTime;

/**
 * SQL 执行历史记录实体
 */
public class SqlHistory {
    private Long id;
    private String userId;
    private Long connectionId;
    private String sqlText;
    private String sqlType;           // SELECT/INSERT/UPDATE/DELETE/DDL/OTHER
    private String targetTable;       // 目标表名
    private String source;            // ai_generated/manual/human_ai_collab
    private Integer executionTime;    // 执行耗时(毫秒)
    private Integer rowsAffected;
    private String status;            // success/error
    private String errorMessage;
    private Boolean isVisible;        // 软删除标记
    private LocalDateTime executedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getExecutionTime() { return executionTime; }
    public void setExecutionTime(Integer executionTime) { this.executionTime = executionTime; }

    public Integer getRowsAffected() { return rowsAffected; }
    public void setRowsAffected(Integer rowsAffected) { this.rowsAffected = rowsAffected; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Boolean getIsVisible() { return isVisible; }
    public void setIsVisible(Boolean isVisible) { this.isVisible = isVisible; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
