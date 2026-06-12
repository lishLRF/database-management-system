package com.dbmanager.controller;

import com.dbmanager.entity.QueryResult;
import com.dbmanager.service.SqlExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final SqlExecutionService sqlExecutionService;

    public SqlController(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeSQL(@RequestBody Map<String, Object> request) {
        try {
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String sql = request.get("sql").toString();
            Boolean confirmed = (Boolean) request.getOrDefault("confirmed", false);

            if (!confirmed) {
                Map<String, Object> dangerCheck = sqlExecutionService.isDangerousOperation(sql);
                if ((Boolean) dangerCheck.get("isDangerous")) {
                    return ResponseEntity.ok(dangerCheck);
                }
            }

            String upperSql = sql.trim().toUpperCase();
            if (upperSql.startsWith("SELECT")) {
                Integer page = request.get("page") != null ? Integer.valueOf(request.get("page").toString()) : null;
                Integer pageSize = request.get("pageSize") != null ? Integer.valueOf(request.get("pageSize").toString()) : null;
                QueryResult result = sqlExecutionService.executeQuery(connectionId, sql, page, pageSize);
                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> result = sqlExecutionService.executeUpdate(connectionId, sql);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
