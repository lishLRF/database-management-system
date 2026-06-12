package com.dbmanager.controller;

import com.dbmanager.service.AiConfigService;
import com.dbmanager.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AiService aiService;

    public AiConfigController(AiConfigService aiConfigService, AiService aiService) {
        this.aiConfigService = aiConfigService;
        this.aiService = aiService;
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            return ResponseEntity.ok(Map.of("success", true, "config", aiConfigService.getConfig(userId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
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
}
