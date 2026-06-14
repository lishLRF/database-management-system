package com.dbmanager.controller;

import com.dbmanager.entity.AiConversation;
import com.dbmanager.repository.AiConversationRepository;
import com.dbmanager.service.AgentService;
import com.dbmanager.service.AiConfigService;
import com.dbmanager.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.PrintWriter;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AiService aiService;
    private final AgentService agentService;
    private final AiConversationRepository convRepo;
    private final ObjectMapper om = new ObjectMapper();

    public AiConfigController(AiConfigService aiConfigService, AiService aiService,
                               AgentService agentService, AiConversationRepository convRepo) {
        this.aiConfigService = aiConfigService;
        this.aiService = aiService;
        this.agentService = agentService;
        this.convRepo = convRepo;
    }

    // ==================== Agent SSE endpoint ====================

    @PostMapping("/agent-chat")
    public void agentChat(@RequestBody Map<String, Object> request, Authentication auth,
                           HttpServletResponse response) throws Exception {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String userId = auth != null ? auth.getName() : "anonymous";
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String message = (String) request.get("message");
        String tableName = (String) request.get("tableName");
        String conversationId = (String) request.get("conversationId");
        String operationType = request.containsKey("operationType") ? (String) request.get("operationType") : "normal";
        String intentType = request.containsKey("intentType") ? (String) request.get("intentType") : "general";

        System.err.println("[Agent] Request opType=" + operationType + " intent=" + intentType + " table=" + tableName);

        PrintWriter writer = response.getWriter();
        Object lock = new Object();

        try {
            String sql = agentService.runAgent(userId, connectionId, message, tableName, conversationId,
                operationType, intentType, event -> {
                synchronized (lock) {
                    writer.write(event);
                    writer.flush();
                }
            });
            synchronized (lock) {
                writer.write("event: agent_result\ndata: " + om.writeValueAsString(Map.of("sql",sql)) + "\n\n");
                writer.flush();
            }
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown";
            // Truncate long error messages (e.g. API response bodies)
            if (errMsg.length() > 500) errMsg = errMsg.substring(0, 500) + "...";
            System.err.println("[Agent ERROR] " + errMsg);
            e.printStackTrace(System.err);
            synchronized (lock) {
                try {
                    writer.write("event: error\ndata: " + om.writeValueAsString(Map.of("message",errMsg)) + "\n\n");
                    writer.write("event: agent_result\ndata: " + om.writeValueAsString(Map.of("sql","","error",errMsg)) + "\n\n");
                    writer.flush();
                } catch (Exception ex) {
                    System.err.println("[Agent SSE write error] " + ex.getMessage());
                }
            }
            } finally {
            try { writer.close(); } catch (Exception ignored) {}
        }
    }

    // ==================== SSE streaming endpoint (legacy) ====================

    @PostMapping("/stream-chat")
    public void streamChat(@RequestBody Map<String, Object> request, Authentication auth,
                            HttpServletResponse response) throws Exception {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String userId = auth != null ? auth.getName() : "anonymous";
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String message = (String) request.get("message");
        String tableName = (String) request.get("tableName");
        String conversationId = (String) request.get("conversationId");

        List<AiConversation> history = Collections.emptyList();
        if (conversationId != null && !conversationId.isBlank()) {
            history = convRepo.findByConversationId(conversationId);
        }

        PrintWriter writer = response.getWriter();
        try {
            aiService.streamChatWithAI(userId, connectionId, message, tableName, history, event -> {
                try {
                    writer.write(event);
                    writer.flush();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } catch (Exception e) {
            writer.write("event: error\ndata: {\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}\n\n");
            writer.flush();
        } finally {
            writer.close();
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String message = (String) request.get("message");
            String tableName = (String) request.get("tableName");
            String conversationId = (String) request.get("conversationId");

            List<AiConversation> history = Collections.emptyList();
            if (conversationId != null && !conversationId.isBlank()) {
                history = convRepo.findByConversationId(conversationId);
            }

            // Generate SQL
            String sql;
            if (tableName != null && !tableName.isBlank()) {
                String lmsg = message.toLowerCase();
                if (lmsg.contains("insert") || lmsg.contains("插入") || lmsg.contains("添加") || lmsg.contains("新增") || lmsg.contains("写入")) {
                    Map<String, Object> r = aiService.generateInsertSQL(userId, connectionId, tableName, message, history);
                    sql = (String) r.get("sql");
                } else if (lmsg.contains("update") || lmsg.contains("更新") || lmsg.contains("修改") || lmsg.contains("填充") || lmsg.contains("null") || lmsg.contains("设为") || lmsg.contains("改成")) {
                    Map<String, Object> r = aiService.generateUpdateSQL(userId, connectionId, tableName, message, null, history);
                    sql = (String) r.get("sql");
                } else {
                    sql = aiService.chatWithAI(userId, connectionId, "表: " + tableName + "\n" + message, history);
                }
            } else {
                sql = aiService.chatWithAI(userId, connectionId, message, history);
            }

            // Self-validation
            Map<String, Object> validation = null;
            try {
                validation = aiService.selfValidateSQL(userId, connectionId, sql, message, tableName);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of("success", true, "sql", sql, "validation", validation != null ? validation : Map.of()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Object config = aiConfigService.getConfig(userId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            if (config == null) {
                result.put("config", null);
                result.put("needSetup", true);
            } else {
                result.put("config", config);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return ResponseEntity.status(500).body(err);
        }
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            return ResponseEntity.ok(Map.of("success", true, "config", aiConfigService.saveConfig(userId, request)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/config/test")
    public ResponseEntity<?> testConnection(Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            return ResponseEntity.ok(aiConfigService.testConnection(userId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/project-background")
    public ResponseEntity<?> getProjectBackground(Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            return ResponseEntity.ok(Map.of("success", true, "background", aiConfigService.getProjectBackground(userId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/project-background")
    public ResponseEntity<?> saveProjectBackground(@RequestBody Map<String, String> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            aiConfigService.saveProjectBackground(userId, request.getOrDefault("background", ""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/generate-sql")
    public ResponseEntity<?> generateSQL(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String naturalLanguage = (String) request.get("naturalLanguage");
            String sql = aiService.generateSQL(userId, connectionId, naturalLanguage);
            return ResponseEntity.ok(Map.of("success", true, "sql", sql));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/fix-sql")
    public ResponseEntity<?> fixSQL(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String originalSQL = (String) request.get("originalSQL");
            String errorMessage = (String) request.get("errorMessage");
            String fixedSQL = aiService.fixSQL(userId, connectionId, originalSQL, errorMessage);
            return ResponseEntity.ok(Map.of("success", true, "sql", fixedSQL));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/explain-sql")
    public ResponseEntity<?> explainSQL(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String sql = (String) request.get("sql");
            Map<String, String> explanation = aiService.explainSQL(userId, connectionId, sql);
            return ResponseEntity.ok(Map.of("success", true,
                "outputEffect", explanation.get("outputEffect"),
                "codeStructure", explanation.get("codeStructure")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/generate-insert")
    public ResponseEntity<?> generateInsert(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String tableName = (String) request.get("tableName");
            String description = (String) request.get("description");
            Map<String, Object> result = aiService.generateInsertSQL(userId, connectionId, tableName, description);
            return ResponseEntity.ok(Map.of("success", true, "sql", result.get("sql"),
                "tableName", result.get("tableName"), "columns", result.get("columns")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/generate-update")
    public ResponseEntity<?> generateUpdate(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String tableName = (String) request.get("tableName");
            String description = (String) request.get("description");
            String whereCondition = (String) request.getOrDefault("whereCondition", null);
            Map<String, Object> result = aiService.generateUpdateSQL(userId, connectionId, tableName, description, whereCondition);
            return ResponseEntity.ok(Map.of("success", true, "sql", result.get("sql"),
                "tableName", result.get("tableName"), "columns", result.get("columns")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/generate-update-row")
    public ResponseEntity<?> generateUpdateRow(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String tableName = (String) request.get("tableName");
            Map<String, Object> currentRow = (Map<String, Object>) request.get("currentRow");
            String pkColumn = (String) request.get("pkColumn");
            Object pkValue = request.get("pkValue");
            String userIntent = (String) request.get("userIntent");
            Map<String, Object> result = aiService.generateUpdateRowSQL(userId, connectionId,
                tableName, currentRow, pkColumn, pkValue, userIntent);
            return ResponseEntity.ok(Map.of("success", true, "sql", result.get("sql"),
                "setClause", result.get("setClause"), "tableName", result.get("tableName"),
                "pkColumn", result.get("pkColumn"), "pkValue", result.get("pkValue"),
                "columns", result.get("columns")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== Format repair & generation SSE endpoints ====================

    private String userId(Authentication auth) { return auth != null ? auth.getName() : "anonymous"; }

    @PostMapping("/repair-format")
    public void repairFormat(@RequestBody Map<String, Object> req, Authentication auth,
                             HttpServletResponse resp) throws Exception {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");
        PrintWriter w = resp.getWriter();
        String format = (String) req.get("format");
        String content = (String) req.get("content");
        try {
            w.write("event: phase\ndata: {\"msg\":\"检测格式错误...\"}\n\n"); w.flush();
            aiService.repairFormatStream(userId(auth), format, content, chunk -> { w.write(chunk); w.flush(); });
            w.write("event: done\ndata: {}\n\n"); w.flush();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown";
            if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
            w.write("event: error\ndata: " + om.writeValueAsString(Map.of("message", msg)) + "\n\n");
            w.flush();
        } finally { w.close(); }
    }

    @PostMapping("/generate-format")
    public void generateFormat(@RequestBody Map<String, Object> req, Authentication auth,
                                HttpServletResponse resp) throws Exception {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");
        PrintWriter w = resp.getWriter();
        String format = (String) req.get("format");
        String description = (String) req.get("description");
        try {
            w.write("event: phase\ndata: {\"msg\":\"生成 "+format.toUpperCase()+" ...\"}\n\n"); w.flush();
            aiService.generateFormatStream(userId(auth), format, description, chunk -> { w.write(chunk); w.flush(); });
            w.write("event: done\ndata: {}\n\n"); w.flush();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown";
            if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
            w.write("event: error\ndata: " + om.writeValueAsString(Map.of("message", msg)) + "\n\n");
            w.flush();
        } finally { w.close(); }
    }

    /** Optimize SQL based on EXPLAIN plan (SSE stream) */
    @PostMapping("/optimize-sql")
    public void optimizeSQL(@RequestBody Map<String, Object> req, Authentication auth, HttpServletResponse resp) throws Exception {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");
        PrintWriter w = resp.getWriter();

        String sqlText = (String) req.get("sqlText");
        String explainPlan = (String) req.get("explainPlan");
        String dbType = (String) req.get("dbType");
        Long connectionId = ((Number) req.get("connectionId")).longValue();

        try {
            w.write("event: phase\ndata: {\"msg\":\"🤖 分析执行计划...\"}\n\n"); w.flush();

            // Build optimize prompt with plan embedded
            String userPrompt = String.format("""
                请分析这个SQL的执行计划，并给出优化建议。

                ## 原始SQL
                %s

                ## 执行计划
                %s

                ## 数据库类型
                %s

                要求：
                1. 指出性能瓶颈（Seq Scan、高cost、missing index）
                2. 生成优化SQL（CREATE INDEX 或改写查询）
                3. 用 [SQL:...] 格式输出最终SQL
                """, sqlText, explainPlan, dbType);

            w.write("event: agent_start\ndata: {\"msg\":\"Agent 开始分析...\"}\n\n"); w.flush();

            // Run agent (blocks until done)
            String result = agentService.runAgent(
                userId(auth),
                connectionId,
                userPrompt,
                null,  // tableName
                null,  // conversationId
                "query",  // operationType
                "optimize",  // intentType (new)
                event -> {
                    try {
                        w.write("event: agent_progress\ndata: " + om.writeValueAsString(event) + "\n\n");
                        w.flush();
                    } catch (Exception ignored) {}
                });

            w.write("event: result\ndata: " + om.writeValueAsString(Map.of("sql", result)) + "\n\n"); w.flush();
            w.write("event: done\ndata: {}\n\n"); w.flush();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown";
            if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
            w.write("event: error\ndata: " + om.writeValueAsString(Map.of("message", msg)) + "\n\n");
            w.flush();
        } finally { w.close(); }
    }
}
