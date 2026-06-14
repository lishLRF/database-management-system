package com.dbmanager.controller;

import com.dbmanager.entity.QueryResult;
import com.dbmanager.service.DynamicDataSourceService;
import com.dbmanager.service.SqlExecutionService;
import com.dbmanager.service.SqlHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final SqlExecutionService sqlExecutionService;
    private final SqlHistoryService sqlHistoryService;
    private final DynamicDataSourceService dataSourceService;

    public SqlController(SqlExecutionService sqlExecutionService, SqlHistoryService sqlHistoryService,
                         DynamicDataSourceService dataSourceService) {
        this.sqlExecutionService = sqlExecutionService;
        this.sqlHistoryService = sqlHistoryService;
        this.dataSourceService = dataSourceService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeSQL(@RequestBody Map<String, Object> request, Authentication auth) {
        Long connectionId = null;
        String sql = null;
        String userId = auth != null ? auth.getName() : "anonymous";
        String source = (String) request.getOrDefault("source", "manual"); // ai_generated/manual/human_ai_collab

        try {
            connectionId = Long.valueOf(request.get("connectionId").toString());
            sql = request.get("sql").toString();
            Boolean confirmed = (Boolean) request.getOrDefault("confirmed", false);

            if (!confirmed) {
                Map<String, Object> dangerCheck = sqlExecutionService.isDangerousOperation(sql);
                if ((Boolean) dangerCheck.get("isDangerous")) {
                    return ResponseEntity.ok(dangerCheck);
                }
            }

            long startTime = System.currentTimeMillis();
            String upperSql = sql.trim().toUpperCase();

            if (upperSql.startsWith("SELECT")) {
                Integer page = request.get("page") != null ? Integer.valueOf(request.get("page").toString()) : null;
                Integer pageSize = request.get("pageSize") != null ? Integer.valueOf(request.get("pageSize").toString()) : null;
                QueryResult result = sqlExecutionService.executeQuery(connectionId, sql, page, pageSize);

                // 记录历史（成功）
                long executionTime = System.currentTimeMillis() - startTime;
                sqlHistoryService.recordHistory(userId, connectionId, sql, source,
                    (int)executionTime, result.getRows().size(), "success", null);

                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> result = sqlExecutionService.executeUpdate(connectionId, sql);

                // 记录历史（成功）
                long executionTime = System.currentTimeMillis() - startTime;
                int rowsAffected = result.get("rowsAffected") != null ? (Integer)result.get("rowsAffected") : 0;
                sqlHistoryService.recordHistory(userId, connectionId, sql, source,
                    (int)executionTime, rowsAffected, "success", null);

                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            // 记录历史（失败）
            if (connectionId != null && sql != null) {
                sqlHistoryService.recordHistory(userId, connectionId, sql, source,
                    0, 0, "error", e.getMessage());
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/explain")
    public ResponseEntity<?> explainSQL(@RequestBody Map<String, Object> request) {
        try {
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String sql = request.get("sql").toString();
            QueryResult result = sqlExecutionService.explainQuery(connectionId, sql);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String tableName,
        @RequestParam(required = false) String sqlType,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        Authentication auth
    ) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            var result = sqlHistoryService.search(userId, tableName, sqlType, search, page, pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistory(@PathVariable Long id, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            sqlHistoryService.softDelete(id, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/history/clear")
    public ResponseEntity<?> clearHistory(Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            sqlHistoryService.clearAll(userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  TABLE ROW BROWSING  (for 智能填表 → 行编辑 mode)
    // ═══════════════════════════════════════════════════════

    /** Get distinct values for a column (for filter dropdowns) */
    @GetMapping("/table/{tableName}/column-values")
    public ResponseEntity<?> getColumnValues(@PathVariable String tableName,
                                              @RequestParam String column,
                                              @RequestParam Long connectionId) {
        try {
            // Validate column exists in table schema
            Map<String, Object> schema = dataSourceService.getSchema(connectionId);
            List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
            Map<String, Object> targetTable = null;
            for (Map<String, Object> t : tables) {
                if (tableName.equalsIgnoreCase((String) t.get("name"))) { targetTable = t; break; }
            }
            if (targetTable == null) return ResponseEntity.badRequest().body(Map.of("error", "Table not found"));
            List<Map<String, Object>> cols = (List<Map<String, Object>>) targetTable.get("columns");
            boolean colExists = cols != null && cols.stream().anyMatch(c -> column.equalsIgnoreCase((String) c.get("name")));
            if (!colExists) return ResponseEntity.badRequest().body(Map.of("error", "Column not found"));

            String sql = "SELECT DISTINCT \"" + column + "\" FROM \"" + tableName
                       + "\" WHERE \"" + column + "\" IS NOT NULL ORDER BY \"" + column + "\"";
            QueryResult result = sqlExecutionService.executeQuery(connectionId, sql, 1, 500);
            List<String> values = new ArrayList<>();
            for (Map<String, Object> row : result.getRows()) {
                Object v = row.get(column);
                if (v != null) values.add(v.toString());
            }
            return ResponseEntity.ok(Map.of("success", true, "values", values));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Search rows with filter conditions */
    @PostMapping("/table/{tableName}/rows")
    public ResponseEntity<?> searchRows(@PathVariable String tableName,
                                         @RequestBody Map<String, Object> request) {
        try {
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            List<Map<String, String>> filters = (List<Map<String, String>>) request.getOrDefault("filters", List.of());

            // Build WHERE clause with validated columns
            Map<String, Object> schema = dataSourceService.getSchema(connectionId);
            List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
            Map<String, Object> targetTable = null;
            for (Map<String, Object> t : tables) {
                if (tableName.equalsIgnoreCase((String) t.get("name"))) { targetTable = t; break; }
            }
            if (targetTable == null) return ResponseEntity.badRequest().body(Map.of("error", "Table not found"));

            List<Map<String, Object>> columns = (List<Map<String, Object>>) targetTable.get("columns");
            Set<String> validCols = new HashSet<>();
            if (columns != null) columns.forEach(c -> validCols.add(((String) c.get("name")).toLowerCase()));

            // Build safe inlined SQL (column names already validated against schema;
            // values are safely quoted to prevent injection)
            StringBuilder safeSql = new StringBuilder("SELECT * FROM \"" + tableName + "\"");
            int idx = 0;
            for (Map<String, String> f : filters) {
                String col = f.get("column");
                String val = f.get("value");
                if (col == null || col.isBlank() || !validCols.contains(col.toLowerCase())) continue;
                safeSql.append(idx == 0 ? " WHERE " : " AND ");
                safeSql.append("\"").append(col).append("\" = '")
                        .append(val.replace("'", "''")).append("'");
                idx++;
            }
            QueryResult result = sqlExecutionService.executeQuery(connectionId, safeSql.toString(), 1, 200);
            List<String> pkCols = dataSourceService.getPrimaryKeys(connectionId, tableName);
            return ResponseEntity.ok(Map.of("success", true, "rows", result.getRows(),
                "columns", targetTable.get("columns"), "pkColumns", pkCols));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get a single row by primary key value */
    @GetMapping("/table/{tableName}/rows/{pkValue}")
    public ResponseEntity<?> getRowByPk(@PathVariable String tableName,
                                         @PathVariable String pkValue,
                                         @RequestParam Long connectionId) {
        try {
            List<String> pkCols = dataSourceService.getPrimaryKeys(connectionId, tableName);
            if (pkCols.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "No primary key on table"));
            String pkCol = pkCols.get(0);

            String sql = "SELECT * FROM \"" + tableName + "\" WHERE \"" + pkCol + "\" = '"
                       + pkValue.replace("'", "''") + "'";
            QueryResult result = sqlExecutionService.executeQuery(connectionId, sql, 1, 1);
            if (result.getRows().isEmpty()) return ResponseEntity.ok(Map.of("success", true, "row", null));
            return ResponseEntity.ok(Map.of("success", true, "row", result.getRows().get(0), "pkColumn", pkCol));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
