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

        // Collect column names and type names
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnName(i));
        }
        result.setColumns(columns);

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                Object raw = rs.getObject(i + 1);
                String colTypeName = "";
                try { colTypeName = metaData.getColumnTypeName(i + 1); } catch (Exception ignored) {}
                row.put(colName, normalizeValue(raw, colTypeName));
            }
            rows.add(row);
        }
        result.setRows(rows);
        result.setTotal(rows.size());

        return result;
    }

    /**
     * Normalize JDBC raw objects to JSON-serializable values.
     * Handles PostgreSQL jsonb/json, arrays, hstore, and generic PGobject.
     * For SQLite and other DBs, just returns raw value.
     */
    private Object normalizeValue(Object raw, String columnTypeName) {
        if (raw == null) return null;

        String typeName = (columnTypeName != null) ? columnTypeName.toLowerCase() : "";

        // PostgreSQL PGobject (jsonb, json, xml, etc.)
        if (raw instanceof org.postgresql.util.PGobject pg) {
            String pgType = pg.getType() != null ? pg.getType().toLowerCase() : "";
            String value = pg.getValue();
            if (value == null) return null;

            // JSON types → validate and return valid JSON string
            if (pgType.equals("jsonb") || pgType.equals("json")) {
                try {
                    // Validate by parsing — ensures Jackson serializes correctly
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
                    return value; // valid JSON string
                } catch (Exception e) {
                    return value; // invalid JSON, return as-is
                }
            }
            return value; // other PG types: return string value
        }

        // PostgreSQL Array → JSON array string
        if (raw instanceof java.sql.Array arr) {
            try {
                Object[] elements = (Object[]) arr.getArray();
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.Arrays.asList(elements));
            } catch (Exception e) {
                return raw.toString();
            }
        }

        // PostgreSQL HSTORE → Map → JSON string
        if (typeName.equals("hstore") && raw instanceof String s) {
            return s; // HSTORE comes as string from JDBC driver
        }

        // Generic Map (some JDBC drivers return Map for complex types)
        if (raw instanceof java.util.Map) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(raw);
            } catch (Exception e) {
                return raw.toString();
            }
        }

        return raw; // plain scalar — no conversion needed
    }

    /**
     * Execute EXPLAIN on a query and return the execution plan.
     * PostgreSQL: EXPLAIN (FORMAT JSON) → single row with JSON plan
     * SQLite: EXPLAIN QUERY PLAN → tabular plan rows
     */
    public QueryResult explainQuery(Long connectionId, String sql) throws Exception {
        HikariDataSource ds = connectionService.getOrCreateDataSource(connectionId);
        long startTime = System.currentTimeMillis();
        String dbType = connectionService.getConnection(connectionId).getDbType();

        String explainSql;
        if ("postgresql".equalsIgnoreCase(dbType)) {
            explainSql = "EXPLAIN (FORMAT JSON) " + sql;
        } else {
            // SQLite and others
            explainSql = "EXPLAIN QUERY PLAN " + sql;
        }

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(explainSql)) {

            QueryResult result = formatResult(rs);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }
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
