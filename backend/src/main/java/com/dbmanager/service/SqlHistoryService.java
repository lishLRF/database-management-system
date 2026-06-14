package com.dbmanager.service;

import com.dbmanager.entity.SqlHistory;
import com.dbmanager.repository.SqlHistoryRepository;
import com.dbmanager.util.SqlParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SqlHistoryService {

    private final SqlHistoryRepository sqlHistoryRepository;

    public SqlHistoryService(SqlHistoryRepository sqlHistoryRepository) {
        this.sqlHistoryRepository = sqlHistoryRepository;
    }

    /**
     * 记录 SQL 执行历史
     */
    public void recordHistory(String userId, Long connectionId, String sql, String source,
                               int executionTime, int rowsAffected, String status, String errorMessage) {
        SqlParser.ParseResult parseResult = SqlParser.parse(sql);

        SqlHistory history = new SqlHistory();
        history.setUserId(userId);
        history.setConnectionId(connectionId);
        history.setSqlText(sql);
        history.setSqlType(parseResult.getSqlType());
        history.setTargetTable(parseResult.getTargetTable());
        history.setSource(source); // ai_generated/manual/human_ai_collab
        history.setExecutionTime(executionTime);
        history.setRowsAffected(rowsAffected);
        history.setStatus(status);
        history.setErrorMessage(errorMessage);
        history.setIsVisible(true);

        sqlHistoryRepository.insert(history);
    }

    /**
     * 查询最近的可见历史（默认50条）
     */
    public List<SqlHistory> getRecentHistory(String userId, int limit) {
        return sqlHistoryRepository.findVisibleByUserId(userId, limit);
    }

    /**
     * 搜索历史（按表名/SQL类型/文本搜索，支持分页）
     */
    public Map<String, Object> search(String userId, String tableName, String sqlType,
                                       String search, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<SqlHistory> list = sqlHistoryRepository.search(userId, tableName, sqlType, search, offset, pageSize);
        int total = sqlHistoryRepository.count(userId, tableName, sqlType, search);
        return Map.of("history", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 软删除单条历史
     */
    public void softDelete(Long id, String userId) {
        sqlHistoryRepository.softDelete(id, userId);
    }

    /**
     * 清空所有历史（批量软删除）
     */
    public void clearAll(String userId) {
        sqlHistoryRepository.clearAll(userId);
    }
}
