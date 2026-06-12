package com.dbmanager.service;

import com.dbmanager.entity.AiConfig;
import com.dbmanager.entity.AiConversation;
import com.dbmanager.entity.DbConnection;
import com.dbmanager.repository.AiConfigRepository;
import com.dbmanager.util.AESUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiService {

    private final AiConfigRepository configRepo;
    private final AESUtil aesUtil;
    private final DynamicDataSourceService dataSourceService;
    private final ConnectionService connectionService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(AiConfigRepository configRepo, AESUtil aesUtil,
                     DynamicDataSourceService dataSourceService, ConnectionService connectionService) {
        this.configRepo = configRepo;
        this.aesUtil = aesUtil;
        this.dataSourceService = dataSourceService;
        this.connectionService = connectionService;
    }

    // ==================== generateSQL ====================

    public String generateSQL(String userId, Long connectionId, String naturalLanguage) throws Exception {
        return generateSQLWithHistory(userId, connectionId, naturalLanguage, Collections.emptyList());
    }

    public String generateSQLWithHistory(String userId, Long connectionId, String naturalLanguage,
                                          List<AiConversation> history) throws Exception {
        AiConfig config = configRepo.findByUserId(userId);
        if (config == null) throw new RuntimeException("请先在AI配置页面配置API信息");

        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        DbConnection dbConn = connectionService.getConnection(connectionId);
        String dbType = dbConn != null ? dbConn.getDbType() : "postgresql";

        String systemPrompt = buildGeneratePrompt(dbType, schemaJson);

        // Build conversation context
        StringBuilder userMsgBuilder = new StringBuilder();
        if (!history.isEmpty()) {
            userMsgBuilder.append("## 对话历史\n");
            for (AiConversation msg : history) {
                String role = "ai".equals(msg.getMessageType()) ? "AI (SQL)" : "User";
                userMsgBuilder.append(role).append(": ").append(msg.getMessageContent()).append("\n\n");
            }
            userMsgBuilder.append("---\n\n");
        }
        userMsgBuilder.append("请将以下自然语言转换为SQL语句（只返回SQL，不要解释）：\n\n");
        userMsgBuilder.append(naturalLanguage);

        return callAI(config, systemPrompt, userMsgBuilder.toString(), 0.7, false);
    }

    private String buildGeneratePrompt(String dbType, String schemaJson) {
        String dbSpecific;
        if ("sqlite".equals(dbType)) {
            dbSpecific = """
                ## SQLite-Specific Rules
                - SQLite does NOT support pg_tables, information_schema, or any PostgreSQL system tables
                - To list tables in SQLite use: SELECT name FROM sqlite_master WHERE type='table' ORDER BY name
                - SQLite has limited DDL support (no ALTER COLUMN, no DROP COLUMN in older versions)
                - String concatenation: use || not CONCAT()
                - Auto-increment: INTEGER PRIMARY KEY (NOT SERIAL/BIGSERIAL)
                """;
        } else {
            dbSpecific = """
                ## PostgreSQL-Specific Rules
                - Use information_schema.tables or pg_catalog.pg_tables for metadata queries
                - Use SERIAL/BIGSERIAL for auto-increment
                - Use ILIKE for case-insensitive matching
                - String concatenation: use || or CONCAT()
                """;
        }

        return """
            You are a professional SQL generation assistant for **%s**.

            ## Database Schema (tables and columns the user has access to)
            ```json
            %s
            ```

            %s

            ## General Rules
            1. Return ONLY the SQL statement — no explanations, no markdown, no code fences
            2. Use EXACT table and column names from the schema above — never invent names
            3. Only the tables/columns listed in the schema exist — do NOT reference any other tables
            4. Only add LIMIT if the user explicitly asks for a specific number of rows
            5. Never put LIMIT inside subqueries or CTEs — only at the outermost level
            6. Do NOT end SQL with a semicolon (;)
            7. For listing tables, use the database-appropriate method listed above
            """.formatted(dbType.toUpperCase(), schemaJson, dbSpecific);
    }

    // ==================== fixSQL ====================

    public String fixSQL(String userId, Long connectionId, String originalSQL, String errorMessage) throws Exception {
        AiConfig config = configRepo.findByUserId(userId);
        if (config == null) throw new RuntimeException("请先在AI配置页面配置API信息");

        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        DbConnection dbConn = connectionService.getConnection(connectionId);
        String dbType = dbConn != null ? dbConn.getDbType() : "postgresql";

        String systemPrompt = """
            You are a SQL debugging expert for **%s** databases.

            ## Database Schema (only these tables/columns exist)
            ```json
            %s
            ```

            ## Response Format
            1. First line: brief explanation of the error in Chinese
            2. Then blank line
            3. Then the corrected SQL ONLY (no explanations, no markdown fences)

            ## Rules
            - Check every column/table name against the schema — do NOT invent names
            - Do NOT add LIMIT unless the original SQL had it
            - Never put LIMIT inside subqueries or CTEs
            - Do NOT end SQL with a semicolon (;)
            - If original uses pg_tables/information_schema on SQLite, replace with sqlite_master
            """.formatted(dbType.toUpperCase(), schemaJson);

        return callAI(config, systemPrompt,
            "Original SQL:\n" + originalSQL + "\n\nError:\n" + errorMessage,
            0.3, true);
    }

    // ==================== explainSQL ====================

    public Map<String, String> explainSQL(String userId, Long connectionId, String sql) throws Exception {
        AiConfig config = configRepo.findByUserId(userId);
        if (config == null) throw new RuntimeException("请先在AI配置页面配置API信息");

        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        DbConnection dbConn = connectionService.getConnection(connectionId);
        String dbType = dbConn != null ? dbConn.getDbType() : "postgresql";

        String systemPrompt = """
            You are a SQL code explainer for **%s** databases.

            ## Database Schema
            ```json
            %s
            ```

            ## Task
            Explain the following SQL code in Chinese. Your response MUST have exactly two sections separated by "---":

            ## Part 1: 预测输出效果
            Describe what results this SQL will produce. What kind of data will the user see? How many rows (estimate)? What columns? Mention specific table/column names from the schema.

            ## Part 2: 代码结构与函数
            Explain the structure step by step: each clause (SELECT/FROM/JOIN/WHERE/GROUP BY/etc.), each function used, and why they are used together. Explain what each part contributes.

            ## Rules
            - Use plain Chinese, no markdown
            - Be specific — reference actual table/column names
            - Part 1 should be 2-4 sentences
            - Part 2 should be 3-6 sentences
            """.formatted(dbType.toUpperCase(), schemaJson);

        String response = callAI(config, systemPrompt,
            "请解释以下SQL：\n" + sql,
            0.3, false);

        Map<String, String> result = new LinkedHashMap<>();
        String[] parts = response.split("---");
        if (parts.length >= 2) {
            result.put("outputEffect", parts[0].replace("Part 1: 预测输出效果", "").replace("## Part 1: 预测输出效果", "").trim());
            result.put("codeStructure", parts[1].replace("Part 2: 代码结构与函数", "").replace("## Part 2: 代码结构与函数", "").trim());
        } else {
            result.put("outputEffect", response);
            result.put("codeStructure", "无法解析结构说明");
        }
        return result;
    }

    // ==================== generateInsert ====================

    public Map<String, Object> generateInsertSQL(String userId, Long connectionId, String tableName,
                                                  String description) throws Exception {
        AiConfig config = configRepo.findByUserId(userId);
        if (config == null) throw new RuntimeException("请先在AI配置页面配置API信息");

        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        DbConnection dbConn = connectionService.getConnection(connectionId);
        String dbType = dbConn != null ? dbConn.getDbType() : "postgresql";

        // Get specific table columns
        List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
        Map<String, Object> targetTable = null;
        for (Map<String, Object> t : tables) {
            if (tableName.equalsIgnoreCase((String) t.get("name"))) {
                targetTable = t;
                break;
            }
        }
        String tableSchema = targetTable != null
            ? objectMapper.writeValueAsString(targetTable)
            : "Table not found in schema";

        String systemPrompt = """
            You are a data entry assistant for **%s** databases.

            ## Target Table Schema
            ```json
            %s
            ```

            ## Task
            Generate an INSERT SQL statement based on the user's natural language description.
            Infer reasonable values for each column based on the description.

            ## Rules
            1. Return ONLY the INSERT SQL statement — no explanations, no markdown
            2. Use ALL columns from the schema unless they are auto-increment (SERIAL/INTEGER PRIMARY KEY)
            3. Skip the primary key / auto-increment column — let the database generate it
            4. For columns not mentioned in the description, use NULL or sensible defaults
            5. Match column types: strings need quotes, numbers don't, booleans use TRUE/FALSE
            6. Do NOT end with semicolon (;)
            """.formatted(dbType.toUpperCase(), tableSchema);

        String sql = callAI(config, systemPrompt,
            "描述：\n" + description + "\n\n目标表：\n" + tableName,
            0.4, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql);
        result.put("tableName", tableName);

        // Parse column-value pairs from generated INSERT
        if (targetTable != null) {
            result.put("columns", targetTable.get("columns"));
        }
        return result;
    }

    // ==================== AI call ====================

    private String callAI(AiConfig config, String systemPrompt, String userMessage,
                          double temperature, boolean extractFromCodeBlock) throws Exception {
        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", temperature);
        body.put("max_tokens", config.getMaxTokens());

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        body.put("messages", messages);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            @SuppressWarnings({"unchecked", "rawtypes"})
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    if (extractFromCodeBlock) {
                        return extractSQLFromFix(content);
                    }
                    return cleanSQL(content);
                }
            }
            throw new RuntimeException("AI返回为空");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI API调用失败: " + e.getMessage());
        }
    }

    private String extractSQLFromFix(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        StringBuilder sql = new StringBuilder();
        boolean inCode = false;
        for (String line : lines) {
            if (line.trim().startsWith("```sql") || line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                sql.append(line).append("\n");
            }
        }
        String result = sql.toString().trim();
        if (result.isEmpty()) {
            for (String line : lines) {
                String trimmed = line.trim().toUpperCase();
                if (trimmed.startsWith("SELECT") || trimmed.startsWith("INSERT") ||
                    trimmed.startsWith("UPDATE") || trimmed.startsWith("DELETE") ||
                    trimmed.startsWith("CREATE") || trimmed.startsWith("ALTER") ||
                    trimmed.startsWith("DROP")) {
                    result = line + "\n";
                    break;
                }
            }
        }
        return cleanSQL(result);
    }

    private String cleanSQL(String content) {
        if (content == null) return "";
        content = content.replaceAll("```sql\\s*", "");
        content = content.replaceAll("```\\s*", "");
        return content.trim();
    }
}
