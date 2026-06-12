package com.dbmanager.service;

import com.dbmanager.entity.QueryResult;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class SqlExecutionService {

    private static final Set<String> DANGEROUS_KEYWORDS =
        Set.of("DELETE", "DROP", "TRUNCATE", "ALTER");

    private final ConnectionService connectionService;

    public SqlExecutionService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public QueryResult executeQuery(Long connectionId, String sql, Integer page, Integer pageSize) throws Exception {
        HikariDataSource ds = connectionService.getOrCreateDataSource(connectionId);
        long startTime = System.currentTimeMillis();

        String paginatedSql = sql;
        if (page != null && pageSize != null) {
            int offset = (page - 1) * pageSize;
            paginatedSql = sql + " LIMIT " + pageSize + " OFFSET " + offset;
        }

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(paginatedSql)) {

            QueryResult result = formatResult(rs);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            if (page != null && pageSize != null) {
                result.setTotal(getTotalCount(conn, sql));
            }

            return result;
        }
    }

    public Map<String, Object> executeUpdate(Long connectionId, String sql) throws Exception {
        HikariDataSource ds = connectionService.getOrCreateDataSource(connectionId);
        long startTime = System.currentTimeMillis();

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            int affectedRows = stmt.executeUpdate(sql);
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("affectedRows", affectedRows);
            result.put("executionTime", executionTime);
            return result;
        }
    }

    public Map<String, Object> isDangerousOperation(String sql) {
        String upperSql = sql.trim().toUpperCase();
        boolean dangerous = DANGEROUS_KEYWORDS.stream().anyMatch(upperSql::startsWith);

        Map<String, Object> result = new HashMap<>();
        result.put("isDangerous", dangerous);
        if (dangerous) {
            result.put("warningMessage", "此操作可能导致数据丢失或结构变更，请确认是否继续？");
        }
        return result;
    }

    private QueryResult formatResult(ResultSet rs) throws SQLException {
        QueryResult result = new QueryResult();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnName(i));
        }
        result.setColumns(columns);

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns) {
                row.put(column, rs.getObject(column));
            }
            rows.add(row);
        }
        result.setRows(rows);
        result.setTotal(rows.size());

        return result;
    }

    private Integer getTotalCount(Connection conn, String sql) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS count_query";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
}
