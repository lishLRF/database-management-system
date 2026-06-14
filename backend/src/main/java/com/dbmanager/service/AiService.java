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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  PROMPT TEMPLATES  (all in one place for consistency)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final String SQL_ROLE_RULES = """
        You are a SQL generator. Return ONLY the SQL — no explanations, no markdown, no code fences, no semicolons.

        ### CRITICAL RULES
        - Use ONLY table/column names from the provided schema. NEVER invent names.
        - NEVER use `pg_tables`, `information_schema`, `pg_catalog` on SQLite. Use `sqlite_master` instead.
        - Do NOT end with semicolon (;)
        - Auto-increment / serial columns: SKIP them in INSERT (let DB generate). For SQLite: skip INTEGER PRIMARY KEY.
        - NEVER add LIMIT to any SQL statement. Do NOT use LIMIT at all.
        - Quote string values with single quotes. Numbers and booleans WITHOUT quotes.
        - SQLite uses || for concat; PostgreSQL can use || or CONCAT().
        """;

    private static final String INSERT_RULES = """
        ### INSERT-SPECIFIC RULES
        - Generate INSERT INTO ... VALUES for the target table.
        - SKIP auto-increment / serial / identity columns — let the DB generate the value.
        - INCLUDE ALL other columns from the schema.
        - Infer sensible values from the user's description. Use the ACTUAL data from the description — numbers, strings, booleans.
        - For columns the user didn't describe, use DEFAULT if the column has one, otherwise use NULL.
        - When the user says "不要填X" / "don't fill X" / "skip X" / "X不填": set that ONE column to DEFAULT or NULL, but KEEP ALL OTHER VALUES exactly as they were in the previous SQL.
        - CRITICAL: When modifying a previous SQL, ONLY change what the user explicitly asks to change. Preserve everything else.
        """;

    private static final String UPDATE_RULES = """
        ### UPDATE-SPECIFIC RULES
        - Generate UPDATE ... SET ... WHERE for the target table.
        - ALWAYS include a WHERE clause. Never update ALL rows.
        - Only SET columns the user explicitly described or logically inferred.
        - Match column types: strings in quotes, numbers bare, booleans TRUE/FALSE.
        """;

    private static final String UPDATE_ROW_RULES = """
        ### ROW EDIT MODE — UPDATE based on existing row data
        - You are given the current state of one specific row (identified by primary key).
        - The user wants to modify some fields and/or fill in NULL values.
        - ONLY suggest changes for fields the user explicitly mentioned, PLUS fields that are NULL and need filling (if the user asked for AI completion).
        - Fields that already have values and the user didn't mention: PRESERVE them — do NOT include in SET clause.
        - The WHERE clause is already provided (based on primary key) — just generate the SET clause.
        - If user asks to clear a field to NULL: include `"col" = NULL`.
        - Return ONLY the SET clause part (no UPDATE, no WHERE, no semicolons, no markdown, no code fences).
        Example response format:
        SET "material" = '不锈钢', "weight" = 15.5, "supplier" = 'XX厂商'
        """;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  MAIN CHAT ENTRY  (with history)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public String chatWithAI(String userId, Long connectionId, String userMessage,
                             List<AiConversation> history) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        String dbType = getDbType(connectionId);

        String system = buildSystemPrompt(dbType, schemaJson, config);
        String user = buildUserMessage(userMessage, history, null, null);

        return callAI(config, system, user, 0.7, false);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  INSERT GENERATION  (with history for follow-up edits)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Map<String, Object> generateInsertSQL(String userId, Long connectionId, String tableName,
                                                  String description, List<AiConversation> history) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String dbType = getDbType(connectionId);
        Map<String, Object> targetTable = findTable(schema, tableName);
        String tableSchema = targetTable != null ? objectMapper.writeValueAsString(targetTable) : "{}";

        String system = buildSystemPrompt(dbType, tableSchema, config)
            + "\n\n" + INSERT_RULES
            + "\n\nYou are generating an INSERT for table \"" + tableName + "\".";

        // Find previous SQL in history for follow-up edits
        String previousSQL = findPreviousSQL(history);

        String user = buildUserMessage(description, history, previousSQL, tableName);

        String sql = callAI(config, system, user, 0.3, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql); result.put("tableName", tableName);
        if (targetTable != null) result.put("columns", targetTable.get("columns"));
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UPDATE GENERATION  (with history)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Map<String, Object> generateUpdateSQL(String userId, Long connectionId, String tableName,
                                                  String description, String whereCondition,
                                                  List<AiConversation> history) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String dbType = getDbType(connectionId);
        Map<String, Object> targetTable = findTable(schema, tableName);
        String tableSchema = targetTable != null ? objectMapper.writeValueAsString(targetTable) : "{}";

        String system = buildSystemPrompt(dbType, tableSchema, config)
            + "\n\n" + UPDATE_RULES
            + "\n\nYou are generating an UPDATE for table \"" + tableName + "\".";

        String w = (whereCondition != null && !whereCondition.isBlank()) ? "\nWHERE: " + whereCondition : "\nInclude a safe WHERE clause.";
        String previousSQL = findPreviousSQL(history);

        String user = buildUserMessage(description + w, history, previousSQL, tableName);

        String sql = callAI(config, system, user, 0.3, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql); result.put("tableName", tableName);
        if (targetTable != null) result.put("columns", targetTable.get("columns"));
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UPDATE ROW (行编辑: given existing row data, generate SET clause)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Map<String, Object> generateUpdateRowSQL(String userId, Long connectionId,
                                                      String tableName, Map<String, Object> currentRow,
                                                      String pkColumn, Object pkValue,
                                                      String userIntent) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String dbType = getDbType(connectionId);
        Map<String, Object> targetTable = findTable(schema, tableName);
        String tableSchema = targetTable != null ? objectMapper.writeValueAsString(targetTable) : "{}";

        // Detect fill-null intent from keywords
        boolean isFillNull = detectFillNullIntent(userIntent);
        StringBuilder extraRules = new StringBuilder();
        if (isFillNull) {
            extraRules.append("""
                ### FILL-NULL INTENT DETECTED — additional rules:
                - ONLY set columns that are currently NULL. Do NOT modify any column that already has a value.
                - For each NULL column, infer a reasonable value based on patterns/constraints/commonsense.
                - If you cannot infer a reasonable value, skip it (do NOT set it).
                - Be conservative — do NOT invent creative values. Use simple, obvious defaults.
                """);
        }

        // Append FK constraint info
        try {
            Map<String, Map<String, String>> fks = dataSourceService.getForeignKeys(connectionId, tableName);
            if (!fks.isEmpty()) {
                extraRules.append("\n### FOREIGN KEY CONSTRAINTS (MUST respect!):\n");
                var ds = connectionService.getOrCreateDataSource(connectionId);
                for (var entry : fks.entrySet()) {
                    String fkCol = entry.getKey();
                    String refTable = entry.getValue().get("refTable");
                    String refCol = entry.getValue().get("refCol");
                    // For NULL FK columns, query valid reference values
                    if (currentRow.containsKey(fkCol) && currentRow.get(fkCol) == null) {
                        try (var c = ds.getConnection();
                             var s = c.createStatement();
                             var rs = s.executeQuery(
                                 "SELECT DISTINCT \"" + refCol + "\" FROM \"" + refTable + "\" LIMIT 30")) {
                            List<String> vals = new ArrayList<>();
                            while (rs.next()) vals.add(String.valueOf(rs.getObject(1)));
                            extraRules.append("- `").append(fkCol).append("` → references `").append(refTable).append("`(").append(refCol).append("). Valid values: ").append(vals).append("\n");
                        } catch (Exception ignored) {
                            extraRules.append("- `").append(fkCol).append("` → references `").append(refTable).append("`(").append(refCol).append(")\n");
                        }
                    } else {
                        extraRules.append("- `").append(fkCol).append("` → references `").append(refTable).append("`(").append(refCol).append(")\n");
                    }
                }
            }
        } catch (Exception ignored) { /* no FK info available */ }

        String system = buildSystemPrompt(dbType, tableSchema, config)
            + "\n\n" + UPDATE_ROW_RULES
            + extraRules.toString()
            + "\n\nTarget table: \"" + tableName + "\", Primary key: \"" + pkColumn + "\" = " + pkValue;

        StringBuilder user = new StringBuilder();
        user.append("## Current Row Data\n```json\n");
        user.append(objectMapper.writeValueAsString(currentRow));
        user.append("\n```\n\n");
        if (userIntent != null && !userIntent.isBlank()) {
            user.append("## User Modification Intent\n");
            user.append(userIntent);
            user.append("\n");
        }
        user.append("\nReturn ONLY the SET clause. Nothing else.");

        String setClause = callAI(config, system, user.toString(), 0.3, false);

        // Assemble full UPDATE SQL
        String pkVal = pkValue.toString();
        // Quote string PKs, leave numeric PKs bare
        String pkWhere;
        try {
            Double.parseDouble(pkVal); // test if numeric
            pkWhere = "\"" + pkColumn + "\" = " + pkVal;
        } catch (NumberFormatException e) {
            pkWhere = "\"" + pkColumn + "\" = '" + pkVal.replace("'", "''") + "'";
        }
        String fullSQL = "UPDATE \"" + tableName + "\" " + setClause.trim()
                       + " WHERE " + pkWhere;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", fullSQL);
        result.put("setClause", setClause.trim());
        result.put("tableName", tableName);
        result.put("pkColumn", pkColumn);
        result.put("pkValue", pkValue);
        if (targetTable != null) result.put("columns", targetTable.get("columns"));
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  FORMAT REPAIR & GENERATION  (JSON/XML/YAML)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** SSE: repair malformed JSON/XML/YAML */
    public void repairFormatStream(String userId, String format, String content,
                                    Consumer<String> onEvent) throws Exception {
        AiConfig config = getConfig(userId);
        String system = "Fix the malformed %s. Return ONLY the corrected content — no markdown, no explanations, no code fences.".formatted(format.toUpperCase());
        String user = "Fix this %s:\n\n%s".formatted(format.toUpperCase(), content);
        streamRaw(config, system, user, 0.1, chunk -> {
            try { onEvent.accept("event: chunk\ndata: " + objectMapper.writeValueAsString(Map.of("chunk", chunk)) + "\n\n"); } catch (Exception ignored) {}
        });
    }

    /** SSE: NL → structured format */
    public void generateFormatStream(String userId, String format, String description,
                                      Consumer<String> onEvent) throws Exception {
        AiConfig config = getConfig(userId);
        String system = """
            Convert the user's natural language description into %s format.
            Return ONLY the %s content — no markdown, no explanations, no code fences.
            Use proper indentation and formatting.
            """.formatted(format.toUpperCase(), format.toUpperCase());
        streamRaw(config, system, description, 0.3, chunk -> {
            try { onEvent.accept("event: chunk\ndata: " + objectMapper.writeValueAsString(Map.of("chunk", chunk)) + "\n\n"); } catch (Exception ignored) {}
        });
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  FIX SQL  (with better prompt + error sanitization)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public String fixSQL(String userId, Long connectionId, String originalSQL, String errorMessage) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        String dbType = getDbType(connectionId);

        // Sanitize useless error messages
        String cleanError = sanitizeError(errorMessage);

        String system = buildSystemPrompt(dbType, schemaJson, config)
            + "\n\nYou are a SQL DEBUGGER. Fix the error in the SQL below. Return ONLY the corrected SQL — no explanations.";

        return callAI(config, system,
            "### Original SQL\n" + originalSQL + "\n\n### Error Message\n" + cleanError + "\n\n### Instructions\nFix the SQL. Keep all existing data and column values. Return only the fixed SQL.",
            0.3, true);
    }

    private String sanitizeError(String error) {
        if (error == null) return "Unknown SQL execution error";
        if (error.length() < 10) return "SQL execution error: " + error;
        // Strip "AI生成失败" and similar wrappers — pass the raw error
        if (error.startsWith("AI API: ")) return error.substring(8);
        if (error.contains("AI生成失败")) return "SQL execution failed. Check syntax and column names.";
        return error;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SELF-VALIDATION
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Map<String, Object> selfValidateSQL(String userId, Long connectionId, String sql,
                                                String task, String tableName) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);

        String tableInfo = "";
        if (tableName != null && !tableName.isBlank()) {
            Map<String, Object> t = findTable(schema, tableName);
            if (t != null) tableInfo = objectMapper.writeValueAsString(t);
        }

        String prompt = """
            Validate this SQL. Return strict JSON with these 4 checks:

            ## Context
            Task: %s
            Target table: %s
            Schema: ```json\n%s\n```
            SQL: %s

            ## Four checks (each: {"pass":true/false, "reason":"brief Chinese reason"}):
            1. format: Valid SQL syntax? No markdown fences? No trailing semicolons? Proper quoting?
            2. task_match: Does it correctly fulfill the task? Right columns? Right SQL type (INSERT/UPDATE/SELECT)?
            3. schema_trust: Are ALL table/column names real (from the schema)? No invented names?
            4. data_completeness: For INSERT — did it include actual values from the user's description? (Having NULL for unmentioned columns is OK.) For UPDATE — WHERE clause present and safe?

            ## IMPORTANT
            - If tableName is specified, the SQL MUST operate on that exact table.
            - ONLY fail data_completeness if NO real data was filled (all NULL/DEFAULT with no actual values).
            - For INSERT: unmentioned columns being NULL is ACCEPTABLE. Only fail if NO columns have real data.
            - Return ONLY JSON, nothing else.
            """.formatted(task, tableInfo, schemaJson, sql);

        String response = callAI(config, prompt, "Return only the JSON validation result.", 0.1, false);

        try {
            String json = response;
            if (json.contains("{")) json = json.substring(json.indexOf("{"));
            if (json.contains("}")) json = json.substring(0, json.lastIndexOf("}") + 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            return result;
        } catch (Exception e) {
            return Map.of("overall", true, "format", Map.of("pass", true, "reason", "auto-passed"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  EXPLAIN SQL
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Map<String, String> explainSQL(String userId, Long connectionId, String sql) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String dbType = getDbType(connectionId);
        String r = callAI(config, """
            Explain this SQL in Chinese. Two sections separated by "---":
            Part 1: 预测输出效果 (2-4 sentences)
            Part 2: 代码结构与函数 (3-6 sentences)
            Schema: ```json\n%s\n``` Database: %s
            """.formatted(objectMapper.writeValueAsString(schema), dbType.toUpperCase()),
            sql, 0.3, false);
        String[] parts = r.split("---", 2);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("outputEffect", parts.length > 0 ? parts[0].replaceAll("Part\\s*1[:：]", "").replace("预测输出效果", "").trim() : "");
        result.put("codeStructure", parts.length > 1 ? parts[1].replaceAll("Part\\s*2[:：]", "").replace("代码结构与函数", "").trim() : "");
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STREAMING (kept for future use, currently unused)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public void streamChatWithAI(String userId, Long connectionId, String userMessage,
                                  String tableName, List<AiConversation> history,
                                  Consumer<String> onEvent) throws Exception {
        AiConfig config = getConfig(userId);
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = objectMapper.writeValueAsString(schema);
        String dbType = getDbType(connectionId);

        String tableConstraint = "";
        if (tableName != null && !tableName.isBlank()) {
            Map<String, Object> t = findTable(schema, tableName);
            if (t != null) tableConstraint = objectMapper.writeValueAsString(t);
        }

        String system = buildSystemPrompt(dbType, schemaJson, config);
        StringBuilder ctx = new StringBuilder(buildHistoryBlock(history));
        ctx.append("Current request:\n").append(userMessage);

        int max = (tableName != null && !tableName.isBlank()) ? 3 : 1;
        String lastErr = "";

        for (int i = 1; i <= max; i++) {
            onEvent.accept(sse("loop", Map.of("attempt", i, "max", max,
                "msg", i==1?"generating":"retry #"+i+": "+lastErr)));

            StringBuilder full = new StringBuilder();
            try {
                String c = ctx.toString();
                streamRaw(config, system, c, 0.7, chunk -> {
                    full.append(chunk);
                    try { onEvent.accept(sse("thinking", Map.of("chunk", chunk))); } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                lastErr = e.getMessage();
                if (i >= max) { onEvent.accept(sse("error", Map.of("msg", lastErr))); return; }
                continue;
            }

            String sql = cleanSQL(full.toString());
            if (tableName != null && !tableName.isBlank()) {
                String verr = validateTableRefs(sql, tableName);
                if (verr != null) {
                    lastErr = verr;
                    ctx.append("\n---\nPrevious: ").append(sql).append("\nError: ").append(verr);
                    if (i >= max) { onEvent.accept(sse("error", Map.of("msg", "Max retries: " + verr))); return; }
                    continue;
                }
            }
            onEvent.accept(sse("result", Map.of("sql", sql)));
            return;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  PROMPT BUILDERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String buildSystemPrompt(String dbType, String schemaJson, AiConfig config) {
        String projectBg = (config != null && config.getProjectBackground() != null
            && !config.getProjectBackground().isBlank())
            ? "\n### Project Background (long-term memory)\n" + config.getProjectBackground() + "\n" : "";

        return """
            Database: **%s**

            ### Schema (only these tables/columns exist)
            ```json
            %s
            ```
            %s
            %s
            """.formatted(dbType.toUpperCase(), schemaJson, projectBg, SQL_ROLE_RULES);
    }

    private String buildUserMessage(String userMessage, List<AiConversation> history,
                                     String previousSQL, String tableName) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildHistoryBlock(history));

        if (previousSQL != null && !previousSQL.isBlank()) {
            sb.append("### Previously Generated SQL (REFERENCE — modify only what user asks)\n");
            sb.append("```sql\n").append(previousSQL).append("\n```\n\n");
        }

        sb.append("### Current Request\n");
        sb.append(userMessage);
        if (tableName != null && !tableName.isBlank()) {
            sb.append("\n\n(Target table: \"").append(tableName).append("\")");
        }
        return sb.toString();
    }

    private String buildHistoryBlock(List<AiConversation> history) {
        if (history == null || history.isEmpty()) return "";
        List<AiConversation> recent = history.size() > 50
            ? history.subList(history.size() - 50, history.size()) : history;
        StringBuilder sb = new StringBuilder();
        sb.append("### Chat History (").append(recent.size()).append(" recent messages)\n");
        for (AiConversation m : recent) {
            String role = "ai".equals(m.getMessageType()) ? "AI" : "User";
            String c = m.getMessageContent();
            if (c.length() > 500) c = c.substring(0, 500) + "...";
            sb.append(role).append(": ").append(c).append("\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }

    private String findPreviousSQL(List<AiConversation> history) {
        if (history == null) return null;
        // Walk backwards to find the most recent AI message that starts with INSERT/UPDATE/SELECT
        for (int i = history.size() - 1; i >= 0; i--) {
            AiConversation m = history.get(i);
            if ("ai".equals(m.getMessageType())) {
                String c = m.getMessageContent();
                String uc = c.trim().toUpperCase();
                if (uc.startsWith("INSERT") || uc.startsWith("UPDATE") || uc.startsWith("SELECT")) {
                    return c.trim();
                }
            }
        }
        return null;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  VALIDATION
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String validateTableRefs(String sql, String expectedTable) {
        if (expectedTable == null || expectedTable.isBlank() || sql == null || sql.isBlank()) return null;
        Set<String> sys = Set.of("sqlite_master", "information_schema", "pg_catalog", "pg_tables");

        for (String keyword : List.of("INSERT\\s+INTO", "UPDATE")) {
            Matcher m = Pattern.compile(keyword + "\\s+\"?([a-zA-Z_][a-zA-Z0-9_]*)\"?", Pattern.CASE_INSENSITIVE).matcher(sql);
            while (m.find()) {
                if (!m.group(1).equalsIgnoreCase(expectedTable))
                    return keyword.startsWith("INSERT") ? "INSERT target wrong" : "UPDATE target wrong"
                        + ": \"" + m.group(1) + "\" should be \"" + expectedTable + "\"";
            }
        }
        Matcher fm = Pattern.compile("FROM\\s+\"?([a-zA-Z_][a-zA-Z0-9_]*)\"?", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (fm.find()) {
            String t = fm.group(1);
            if (!t.equalsIgnoreCase(expectedTable) && !sys.contains(t.toLowerCase()))
                return "FROM references \"" + t + "\" but expected \"" + expectedTable + "\"";
        }
        return null;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  AI CALLS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String callAI(AiConfig config, String system, String user,
                          double temp, boolean extractFenced) throws Exception {
        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", temp);
        int mt = config.getMaxTokens() != null ? config.getMaxTokens() : 100000;
        body.put("max_tokens", Math.max(mt / 3, 16384));

        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", system));
        msgs.add(Map.of("role", "user", "content", user));
        body.put("messages", msgs);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.exchange(baseUrl + "chat/completions",
                HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map<String, Object> rb = resp.getBody();
            if (rb != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) rb.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                    return extractFenced ? extractSQL(content) : cleanSQL(content);
                }
            }
            throw new RuntimeException("AI returned empty");
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException("AI API: " + e.getMessage()); }
    }

    private void streamRaw(AiConfig config, String sys, String usr,
                            double temp, Consumer<String> onChunk) throws Exception {
        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", temp);
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", true);
        body.put("messages", List.of(
            Map.of("role", "system", "content", sys),
            Map.of("role", "user", "content", usr)
        ));

        String req = objectMapper.writeValueAsString(body);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(req)).build();

        HttpResponse<java.io.InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200)
            throw new RuntimeException("API " + resp.statusCode() + ": " + new String(resp.body().readAllBytes()));

        try (BufferedReader r = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> chunk = objectMapper.readValue(line.substring(6), Map.class);
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                            if (delta != null) {
                                String content = (String) delta.get("content");
                                if (content != null && !content.isEmpty()) onChunk.accept(content);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private String sse(String type, Object data) throws Exception {
        return "event: " + type + "\ndata: " + objectMapper.writeValueAsString(data) + "\n\n";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private AiConfig getConfig(String userId) {
        AiConfig c = configRepo.findByUserId(userId);
        if (c == null) throw new RuntimeException("请先在AI配置页面配置API信息");
        return c;
    }

    private String getDbType(Long connectionId) {
        DbConnection c = connectionService.getConnection(connectionId);
        return c != null ? c.getDbType() : "postgresql";
    }

    /** Detect if user intent is specifically about filling NULL values */
    private boolean detectFillNullIntent(String intent) {
        if (intent == null || intent.isBlank()) return false;
        String s = intent.toLowerCase();
        return s.contains("填写") || s.contains("填充") || s.contains("补全") || s.contains("填满")
            || s.contains("null") || s.contains("空值") || s.contains("空白") || s.contains("空缺")
            || s.contains("补充") || s.contains("补齐") || s.contains("填补");
    }

    private Map<String, Object> findTable(Map<String, Object> schema, String name) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
        for (Map<String, Object> t : tables)
            if (name.equalsIgnoreCase((String) t.get("name"))) return t;
        return null;
    }

    private String extractSQL(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder(); boolean in = false;
        for (String l : lines) { if (l.trim().startsWith("```")) { in = !in; continue; } if (in) sb.append(l).append("\n"); }
        String r = sb.toString().trim();
        if (r.isEmpty()) {
            for (String l : lines) {
                String t = l.trim().toUpperCase();
                if (t.startsWith("SELECT")||t.startsWith("INSERT")||t.startsWith("UPDATE")||t.startsWith("DELETE")||t.startsWith("CREATE")||t.startsWith("ALTER")||t.startsWith("DROP")) { r = l; break; }
            }
        }
        return cleanSQL(r);
    }

    private String cleanSQL(String content) {
        if (content == null) return "";
        return content.replaceAll("```sql\\s*", "").replaceAll("```\\s*", "").trim();
    }

    // kept for compat
    public String generateSQL(String userId, Long cid, String nl) throws Exception { return chatWithAI(userId, cid, nl, List.of()); }
    public String generateSQLWithHistory(String userId, Long cid, String nl, List<AiConversation> h) throws Exception { return chatWithAI(userId, cid, nl, h); }

    // old signature compat
    public Map<String, Object> generateInsertSQL(String userId, Long cid, String tn, String desc) throws Exception {
        return generateInsertSQL(userId, cid, tn, desc, List.of());
    }
    public Map<String, Object> generateUpdateSQL(String userId, Long cid, String tn, String desc, String wc) throws Exception {
        return generateUpdateSQL(userId, cid, tn, desc, wc, List.of());
    }

    //  OPTIMIZE SQL (from EXPLAIN plan)
    public boolean detectOptimizeIntent(String userPrompt) {
        String s = userPrompt.toLowerCase();
        return s.contains("优化") || s.contains("慢") || s.contains("快")
            || s.contains("索引") || s.contains("执行计划") || s.contains("explain")
            || s.contains("performance") || s.contains("slow") || s.contains("optimize")
            || s.contains("加速") || s.contains("提升性能") || s.contains("改进");
    }
}
