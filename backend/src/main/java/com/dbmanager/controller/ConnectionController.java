package com.dbmanager.controller;

import com.dbmanager.entity.DbConnection;
import com.dbmanager.service.ConnectionService;
import com.dbmanager.service.DynamicDataSourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final DynamicDataSourceService dynamicDataSourceService;

    public ConnectionController(ConnectionService connectionService, DynamicDataSourceService dynamicDataSourceService) {
        this.connectionService = connectionService;
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createConnection(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            DbConnection conn = new DbConnection();
            conn.setUserId(userId);
            conn.setName((String) request.get("name"));
            conn.setDbType((String) request.get("dbType"));
            conn.setHost((String) request.get("host"));
            conn.setPort((Integer) request.get("port"));
            conn.setDatabaseName((String) request.get("database"));
            conn.setUsername((String) request.get("username"));
            conn.setIsActive(false);

            DbConnection created = connectionService.createConnection(conn, (String) request.get("password"));
            return ResponseEntity.ok(Map.of("success", true, "connection", created));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConnections(Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        List<DbConnection> connections = connectionService.getAllConnections(userId);
        return ResponseEntity.ok(Map.of("connections", connections));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = connectionService.testConnection(id);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<Map<String, Object>> switchConnection(@PathVariable Long id, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Map<String, Object> schema = dynamicDataSourceService.switchDataSource(userId, id);
            DbConnection connection = connectionService.getConnection(id);
            return ResponseEntity.ok(Map.of("success", true, "schema", schema, "connection", connection));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
