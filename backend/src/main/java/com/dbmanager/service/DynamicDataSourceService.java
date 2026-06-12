package com.dbmanager.service;

import com.dbmanager.repository.ConnectionRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

@Service
public class DynamicDataSourceService {

    private final ConnectionService connectionService;
    private final ConnectionRepository connectionRepository;
    private final ThreadLocal<Long> currentConnectionId = new ThreadLocal<>();

    public DynamicDataSourceService(ConnectionService connectionService, ConnectionRepository connectionRepository) {
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
    }

    public Map<String, Object> switchDataSource(String userId, Long connectionId) throws Exception {
        connectionRepository.updateActive(userId, connectionId);
        currentConnectionId.set(connectionId);
        return getSchema(connectionId);
    }

    public Map<String, Object> getSchema(Long connectionId) throws Exception {
        HikariDataSource ds = connectionService.getOrCreateDataSource(connectionId);
        Map<String, Object> schema = new HashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();

        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    Map<String, Object> table = new HashMap<>();
                    String tableName = rs.getString("TABLE_NAME");
                    table.put("name", tableName);
                    table.put("type", rs.getString("TABLE_TYPE"));
                    table.put("columns", getTableColumns(meta, tableName));
                    tables.add(table);
                }
            }
        }

        schema.put("tables", tables);
        return schema;
    }

    private List<Map<String, Object>> getTableColumns(DatabaseMetaData meta, String tableName) throws Exception {
        List<Map<String, Object>> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                Map<String, Object> col = new HashMap<>();
                col.put("name", rs.getString("COLUMN_NAME"));
                col.put("type", rs.getString("TYPE_NAME"));
                col.put("size", rs.getInt("COLUMN_SIZE"));
                col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                String remarks = rs.getString("REMARKS");
                col.put("comment", remarks != null ? remarks : "");
                columns.add(col);
            }
        }
        return columns;
    }

    public Long getCurrentConnectionId() {
        return currentConnectionId.get();
    }
}
