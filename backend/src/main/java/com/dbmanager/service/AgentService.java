package com.dbmanager.service;

import com.dbmanager.entity.*;
import com.dbmanager.repository.AiConfigRepository;
import com.dbmanager.util.AESUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.*;

@Service
public class AgentService {

    private final AiConfigRepository configRepo;
    private final AESUtil aesUtil;
    private final DynamicDataSourceService dataSourceService;
    private final ConnectionService connectionService;
    private final SqlExecutionService sqlExec;
    private final ObjectMapper om = new ObjectMapper();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);

    private final Map<String, List<ToolCallLog>> toolLogs = new ConcurrentHashMap<>();

    public static class ToolCallLog {
        public String tool; public Map<String,Object> params; public long elapsedMs;
        public String result; public int step;
    }

    public AgentService(AiConfigRepository configRepo, AESUtil aesUtil,
                        DynamicDataSourceService dataSourceService,
                        ConnectionService connectionService,
                        SqlExecutionService sqlExec) {
        this.configRepo = configRepo; this.aesUtil = aesUtil;
        this.dataSourceService = dataSourceService;
        this.connectionService = connectionService;
        this.sqlExec = sqlExec;
    }

    // ═══════════════════════════════════════════════════════
    //  MAIN AGENT ORCHESTRATOR
    // ═══════════════════════════════════════════════════════

    public String runAgent(String userId, Long connectionId, String userMessage,
                           String tableName, String conversationId,
                           String operationType, String intentType,
                           Consumer<String> onEvent) throws Exception {
        System.err.println("[Agent] START userId=" + userId + " connId=" + connectionId + " table=" + tableName + " op=" + operationType + " intent=" + intentType);
        AiConfig config = getConfig(userId);
        System.err.println("[Agent] Config OK model=" + config.getModelName() + " url=" + config.getApiBaseUrl());
        Map<String, Object> schema = dataSourceService.getSchema(connectionId);
        String schemaJson = om.writeValueAsString(schema);
        String dbType = getDbType(connectionId);
        System.err.println("[Agent] Schema loaded dbType=" + dbType + " tables=" + ((List<?>)schema.get("tables")).size());

        toolLogs.put(conversationId, new ArrayList<>());

        // ── PHASE 1: TASK PLANNING ──
        System.err.println("[Agent] Phase 1: Planning...");
        onEvent.accept(event("agent_phase", Map.of("phase","plan","msg","分析需求，拆解任务...")));

        String plan = callAI(config, buildPlanPrompt(dbType, schemaJson, config, tableName, userMessage, operationType, intentType),
            "User request: " + userMessage + (tableName != null ? "\nSelected table: " + tableName : ""),
            0.3, false, outputTokens(config));
        System.err.println("[Agent] Plan received len=" + plan.length());
        System.err.println("[Agent] Plan content: " + plan);

        onEvent.accept(event("plan_result", Map.of("plan", plan.length() > 500 ? plan.substring(0,500)+"..." : plan)));

        List<Map<String, String>> tasks = parseTasks(plan);
        if (tasks.isEmpty()) {
            System.err.println("[Agent] Plan parsing failed, using default task");
            tasks = List.of(Map.of("type","auto","target","","desc",userMessage,"deps",""));
        }
        System.err.println("[Agent] Tasks=" + tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            System.err.println("[Agent]   Task" + i + ": " + tasks.get(i));
        }

        // ── PHASE 2: PARALLEL EXECUTION ──
        System.err.println("[Agent] Phase 2: Executing...");
        onEvent.accept(event("agent_phase", Map.of("phase","execute","msg","执行 "+tasks.size()+" 个子任务...","parallel",true)));

        Map<String, String> taskResults = new ConcurrentHashMap<>();
        int totalTools = executeTasksParallel(config, dbType, schemaJson, connectionId,
            conversationId, tasks, taskResults, tableName, operationType, intentType, onEvent);
        System.err.println("[Agent] Execution done tools=" + totalTools + " results=" + taskResults.size());

        // Build final SQL
        StringBuilder allSQL = new StringBuilder();
        for (Map<String, String> t : tasks) {
            String sql = taskResults.get(t.get("desc"));
            if (sql != null) allSQL.append("-- ").append(t.get("desc")).append("\n").append(sql).append("\n\n");
        }
        String finalSQL = allSQL.toString().trim();
        System.err.println("[Agent] Final SQL len=" + finalSQL.length());
        if (finalSQL.isEmpty()) throw new RuntimeException("Agent failed to generate any SQL");

        // ── PHASE 3: CODE-LEVEL VALIDATION ──
        System.err.println("[Agent] Phase 3: Validate...");
        onEvent.accept(event("agent_phase", Map.of("phase","validate","msg","代码级校验...")));
        Map<String, Object> codeValidation = SqlValidator.check(finalSQL, tableName, dbType);
        onEvent.accept(event("validate_result", codeValidation));

        // ── PHASE 4: AI REVIEW ──
        System.err.println("[Agent] Phase 4: Review...");
        onEvent.accept(event("agent_phase", Map.of("phase","review","msg","AI校核中...")));
        Map<String, Object> review = Reviewer.run(config, aesUtil, om, dbType, schemaJson, finalSQL, userMessage, tableName);
        onEvent.accept(event("review_result", review));

        // ── PHASE 5: FINAL ──
        System.err.println("[Agent] Phase 5: Done");
        boolean overall = review.containsKey("overall") && (boolean)review.get("overall");
        onEvent.accept(event("agent_done", Map.of(
            "steps", tasks.size(),
            "review_passed", overall,
            "tool_calls", totalTools,
            "code_ok", codeValidation.getOrDefault("pass", true)
        )));

        System.err.println("[Agent] RETURN sql=" + finalSQL.substring(0, Math.min(200, finalSQL.length())));
        return finalSQL;
    }

    // ═══════════════════════════════════════════════════════
    //  PARALLEL TASK EXECUTOR
    // ═══════════════════════════════════════════════════════

    private int executeTasksParallel(AiConfig config, String dbType, String schemaJson,
                                      Long connectionId, String conversationId,
                                      List<Map<String, String>> tasks,
                                      Map<String, String> taskResults, String tableName,
                                      String operationType, String intentType,
                                      Consumer<String> onEvent) throws Exception {
        // Build DAG: task index -> list of dependency indices
        int n = tasks.size();
        List<List<Integer>> depsOf = new ArrayList<>();  // what each task depends on
        List<List<Integer>> dependents = new ArrayList<>(); // tasks that depend on this
        for (int i = 0; i < n; i++) { depsOf.add(new ArrayList<>()); dependents.add(new ArrayList<>()); }

        Map<String, Integer> taskIndexByDesc = new HashMap<>();
        for (int i = 0; i < n; i++) {
            taskIndexByDesc.put(tasks.get(i).get("desc"), i);
        }

        for (int i = 0; i < n; i++) {
            String deps = tasks.get(i).getOrDefault("deps", "");
            if (deps != null && !deps.isBlank()) {
                for (String depDesc : deps.split(";")) {
                    depDesc = depDesc.trim();
                    if (!depDesc.isEmpty()) {
                        Integer depIdx = taskIndexByDesc.get(depDesc);
                        if (depIdx == null) {
                            // Fuzzy match
                            for (Map.Entry<String, Integer> e : taskIndexByDesc.entrySet()) {
                                if (e.getKey().contains(depDesc) || depDesc.contains(e.getKey())) {
                                    depIdx = e.getValue(); break;
                                }
                            }
                        }
                        if (depIdx != null && depIdx != i) {
                            depsOf.get(i).add(depIdx);
                            dependents.get(depIdx).add(i);
                        }
                    }
                }
            }
        }

        // Topological sort into waves
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) inDegree[i] = depsOf.get(i).size();

        List<List<Integer>> waves = new ArrayList<>();
        boolean[] done = new boolean[n];
        int remaining = n;

        while (remaining > 0) {
            List<Integer> wave = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!done[i] && inDegree[i] == 0) {
                    wave.add(i);
                    done[i] = true;
                    remaining--;
                }
            }
            if (wave.isEmpty()) {
                // Deadlock fallback: execute all remaining sequentially
                for (int i = 0; i < n; i++) {
                    if (!done[i]) { wave.add(i); done[i] = true; remaining--; }
                }
            }
            waves.add(wave);

            // Update in-degrees
            for (int idx : wave) {
                for (int dep : dependents.get(idx)) {
                    inDegree[dep]--;
                }
            }
        }

        AtomicInteger stepCounter = new AtomicInteger(0);
        AtomicInteger toolCounter = new AtomicInteger(0);

        // Execute waves
        for (int wi = 0; wi < waves.size(); wi++) {
            List<Integer> wave = waves.get(wi);
            boolean parallel = wave.size() > 1;

            if (parallel) {
                onEvent.accept(event("wave_start", Map.of("wave",wi+1,"total",waves.size(),"parallel",true,"count",wave.size())));
            }

            if (wave.size() == 1) {
                // Sequential
                int idx = wave.get(0);
                int step = stepCounter.incrementAndGet();
                int tools = executeSingleTask(config, dbType, schemaJson, connectionId, conversationId,
                    tasks.get(idx), taskResults, tableName, step, n, operationType, intentType, onEvent);
                toolCounter.addAndGet(tools);
            } else {
                // Parallel
                List<Future<Integer>> futures = new ArrayList<>();
                for (int idx : wave) {
                    final int fi = idx;
                    final int step = stepCounter.incrementAndGet();
                    futures.add(threadPool.submit(() ->
                        executeSingleTask(config, dbType, schemaJson, connectionId, conversationId,
                            tasks.get(fi), taskResults, tableName, step, n, operationType, intentType, onEvent)
                    ));
                }
                for (Future<Integer> f : futures) {
                    try { toolCounter.addAndGet(f.get()); }
                    catch (Exception e) { onEvent.accept(event("task_error", Map.of("error", e.getMessage()))); }
                }
            }
        }

        return toolCounter.get();
    }

    private int executeSingleTask(AiConfig config, String dbType, String schemaJson,
                                   Long connectionId, String conversationId,
                                   Map<String, String> task, Map<String, String> taskResults,
                                   String tableName, int step, int total,
                                   String operationType, String intentType,
                                   Consumer<String> onEvent) {
        String taskType = task.getOrDefault("type","auto");
        String taskDesc = task.getOrDefault("desc","");
        String taskTable = task.getOrDefault("target","");
        if (taskTable.isEmpty()) taskTable = tableName;

        onEvent.accept(event("task_start", Map.of("step",step,"total",total,
            "type",taskType,"desc",taskDesc,"table",taskTable)));

        String execPrompt = buildExecutorPrompt(dbType, schemaJson, config, taskType, taskTable, taskDesc, taskResults, operationType, intentType);
        String toolsStr = buildToolsForTask(taskType, taskTable);

        StringBuilder taskConversation = new StringBuilder();
        taskConversation.append("## Task (").append(taskType).append(")\n").append(taskDesc).append("\n");
        if (!taskTable.isEmpty()) taskConversation.append("\nTarget table: ").append(taskTable);

        String lastResponse = "";
        int maxTools = 6; // Reduced from 10 — faster convergence
        int toolsUsed = 0;

        for (int iter = 0; iter < maxTools; iter++) {
            String prompt = execPrompt + "\n\n" + toolsStr + "\n\n" + taskConversation
                + (iter > 0 ? "\n\n---\nLast response: " + lastResponse.substring(0, Math.min(300, lastResponse.length())) : "")
                + "\n\nOutput [TOOL:name{params}] or [SQL:...].";

            try {
                String resp = callAI(config, prompt, "", 0.2, false, outputTokens(config));
                lastResponse = resp;

                // Tool calls
                List<Map.Entry<String, String>> calls = extractToolCalls(resp);
                if (!calls.isEmpty()) {
                    for (Map.Entry<String, String> call : calls) {
                        toolsUsed++;
                        String toolName = call.getKey();
                        Map<String, String> params = parseParams(call.getValue());
                        onEvent.accept(event("tool_call", Map.of("step",step,"tool",toolName,"params",params)));
                        long t0 = System.currentTimeMillis();
                        String result;
                        try { result = executeTool(toolName, params, connectionId, conversationId, step); }
                        catch (Exception e) { result = "Tool error: " + e.getMessage(); }
                        long elapsed = System.currentTimeMillis() - t0;

                        ToolCallLog log = new ToolCallLog();
                        log.tool = toolName; log.params = new HashMap<>(params);
                        log.elapsedMs = elapsed; log.step = step;
                        log.result = result.length() > 800 ? result.substring(0,800) : result;
                        toolLogs.get(conversationId).add(log);

                        onEvent.accept(event("tool_result", Map.of("step",step,"tool",toolName,"elapsed",elapsed,"preview",result.substring(0,Math.min(150,result.length())))));
                        taskConversation.append("\n\n[TOOL RESULT: ").append(toolName).append("]\n").append(result);
                    }
                    continue;
                }

                // SQL extraction
                String sql = extractSQLFromResponse(resp);
                if (sql != null && !sql.isEmpty()) {
                    // Code-level validation
                    Map<String, Object> validation = SqlValidator.check(sql, taskTable, dbType);
                    if (Boolean.FALSE.equals(validation.get("pass"))) {
                        // Quick retry once
                        taskConversation.append("\n\n[VALIDATION FAILED: ").append(validation.get("issues")).append("]");
                        if (iter < maxTools - 1) continue;
                    }

                    taskResults.put(taskDesc, sql);
                    onEvent.accept(event("task_sql", Map.of(
                        "step",step,"total",total,"type",taskType,"desc",taskDesc,
                        "sql",sql,"table",taskTable,
                        "validated",validation.getOrDefault("pass",true),
                        "issues",validation.getOrDefault("issues","")
                    )));
                    return toolsUsed;
                }

                if (iter >= 2) {
                    // Try clean extraction
                    String fb = cleanSQL(lastResponse);
                    if (!fb.isEmpty() && SqlValidator.isSQL(fb)) {
                        taskResults.put(taskDesc, fb);
                        onEvent.accept(event("task_sql", Map.of("step",step,"total",total,"type",taskType,"desc",taskDesc,"sql",fb,"table",taskTable,"status","fallback")));
                        return toolsUsed;
                    }
                    // Aggressive fallback: scan for SQL keyword lines in the response
                    String extracted = extractSQLLines(lastResponse);
                    if (extracted != null) {
                        taskResults.put(taskDesc, extracted);
                        onEvent.accept(event("task_sql", Map.of("step",step,"total",total,"type",taskType,"desc",taskDesc,"sql",extracted,"table",taskTable,"status","fallback-scan")));
                        return toolsUsed;
                    }
                    break;
                }
            } catch (Exception e) {
                onEvent.accept(event("task_error", Map.of("step",step,"error",e.getMessage())));
                break;
            }
        }
        return toolsUsed;
    }

    // ═══════════════════════════════════════════════════════
    //  TOOLS
    // ═══════════════════════════════════════════════════════

    private String buildToolsForTask(String taskType, String tableName) {
        String tn = tableName != null ? tableName : "TABLE";
        return """
            ## Tools
            [TOOL:describe_table{"table":"%s"}] — Column details (names, types, nullable, PK)
            [TOOL:query_data{"sql":"SELECT ... FROM \\"%s\\""}] — Run any SELECT to explore real data. NEVER use LIMIT.
            [TOOL:get_foreign_keys{}] — FK relationships
            [TOOL:search_column{"keyword":"word"}] — Search columns by name
            [TOOL:get_row_count{"table":"%s"}] — Row count

            ⚠️ RULE: Before any INSERT/UPDATE/DELETE, you MUST:
            1. describe_table to see columns
            2. query_data to see existing rows (understand the data!)
            3. get_row_count to know scale

            Output: [TOOL:name{"param":"val"}] or [SQL:...]""".formatted(tn, tn, tn);
    }

    private List<Map.Entry<String, String>> extractToolCalls(String text) {
        List<Map.Entry<String, String>> calls = new ArrayList<>();
        Matcher tm = Pattern.compile("\\[TOOL:(\\w+)\\{([^}]*)\\}\\]").matcher(text);
        while (tm.find()) calls.add(Map.entry(tm.group(1), tm.group(2)));
        return calls;
    }

    private String extractSQLFromResponse(String resp) {
        if (resp == null) return null;
        // [SQL:...]
        Matcher sm = Pattern.compile("\\[SQL:(.*?)\\]", Pattern.DOTALL).matcher(resp);
        if (sm.find()) { String s = sm.group(1).trim(); if (!s.isEmpty()) return s; }
        // Clean SQL keyword start
        String cleaned = cleanSQL(resp);
        if (SqlValidator.isSQL(cleaned)) return cleaned;
        return null;
    }

    private String executeTool(String toolName, Map<String, String> params,
                                Long connectionId, String conversationId, int step) throws Exception {
        return switch (toolName) {
            case "describe_table" -> describeTable(params.getOrDefault("table","").replace("\"",""), connectionId);
            case "query_data" -> queryData(params.getOrDefault("sql","").replace("\"",""), connectionId);
            case "get_foreign_keys" -> getForeignKeys(connectionId);
            case "search_column" -> searchColumn(params.getOrDefault("keyword","").replace("\"",""), connectionId);
            case "get_row_count" -> getRowCount(params.getOrDefault("table","").replace("\"",""), connectionId);
            default -> "Unknown tool: " + toolName;
        };
    }

    private String describeTable(String tableName, Long cid) throws Exception {
        Map<String, Object> schema = dataSourceService.getSchema(cid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
        for (Map<String, Object> t : tables)
            if (tableName.equalsIgnoreCase((String) t.get("name"))) return om.writeValueAsString(t);
        return "Not found: " + tableName;
    }

    private String queryData(String sql, Long cid) {
        try {
            String u = sql.toUpperCase().trim();
            if (!u.startsWith("SELECT") || u.contains("DROP")||u.contains("DELETE")||u.contains("INSERT")||u.contains("UPDATE")||u.contains("TRUNCATE"))
                return "ERROR: Only SELECT allowed";
            var r = sqlExec.executeQuery(cid, sql, 1, 10);
            return om.writeValueAsString(r.getRows());
        } catch (Exception e) { return "Query error: " + e.getMessage(); }
    }

    private String getForeignKeys(Long cid) throws Exception {
        HikariDataSource ds = connectionService.getOrCreateDataSource(cid);
        List<Map<String, String>> fks = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            Map<String, Object> schema = dataSourceService.getSchema(cid);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tableList = (List<Map<String, Object>>) schema.get("tables");
            for (Map<String, Object> t : tableList) {
                String tn = (String) t.get("name");
                try (ResultSet rs = meta.getImportedKeys(null, null, tn)) {
                    while (rs.next()) fks.add(Map.of("table",tn,"column",rs.getString("FKCOLUMN_NAME"),"ref_table",rs.getString("PKTABLE_NAME"),"ref_column",rs.getString("PKCOLUMN_NAME")));
                } catch (Exception ignored) {}
            }
        }
        return om.writeValueAsString(fks);
    }

    private String searchColumn(String keyword, Long cid) throws Exception {
        Map<String, Object> schema = dataSourceService.getSchema(cid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
        List<Map<String, String>> matches = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) t.get("columns");
            if (cols == null) continue;
            for (Map<String, Object> col : cols) {
                String name = (String) col.get("name");
                if (name != null && name.toLowerCase().contains(keyword.toLowerCase()))
                    matches.add(Map.of("table",(String)t.get("name"),"column",name,"type",col.get("type")!=null?col.get("type").toString():""));
            }
        }
        return matches.isEmpty() ? "No matches for '" + keyword + "'" : om.writeValueAsString(matches);
    }

    private String getRowCount(String tableName, Long cid) {
        try {
            var r = sqlExec.executeQuery(cid, "SELECT COUNT(*) AS cnt FROM \""+tableName+"\"", 1, 1);
            return om.writeValueAsString(r.getRows());
        } catch (Exception e) { return "Count error: " + e.getMessage(); }
    }

    // ═══════════════════════════════════════════════════════
    //  PROMPTS
    // ═══════════════════════════════════════════════════════

    private String buildPlanPrompt(String dbType, String schemaJson, AiConfig config, String tableName,
                                     String userMessage, String operationType, String intentType) {
        StringBuilder extra = new StringBuilder();
        // Operation-type-specific rules
        if ("error_fix".equals(operationType)) {
            extra.append("\n## Operation: ERROR FIX — the SQL was previously wrong. Understand the original intent, explore the data, then fix.\n");
        } else if ("human_suggestion".equals(operationType)) {
            extra.append("\n## Operation: HUMAN SUGGESTION — a human has given specific modification advice. Respect it strictly while keeping original intent.\n");
        }
        // Intent-type-specific rules
        extra.append("## Intent: ").append(intentType.toUpperCase()).append("\n");
        switch (intentType) {
            case "query":
                extra.append("Query-only mode. Plan 1-2 SELECT tasks. NO explore needed (skip describe_table for query).\n");
                break;
            case "insert":
                extra.append("INSERT mode. MANDATORY: plan TWO tasks — (1) explore task with type='explore' to describe_table+query_data, (2) insert task with type='insert' that depends on explore task.\n");
                break;
            case "update":
                extra.append("UPDATE mode. MANDATORY: plan TWO tasks — (1) explore task type='explore', (2) update task type='update' depends on explore.\n");
                break;
            case "delete":
                extra.append("DELETE mode. MANDATORY: plan TWO tasks — (1) explore task type='explore', (2) delete task type='delete' depends on explore.\n");
                break;
            case "modify":
                extra.append("Modify/fix mode. Plan explore FIRST to understand current state, then modify.\n");
                break;
            default:
                extra.append("General mode. Plan minimal tasks. Explore if write, skip if query.\n");
        }
        return """
            You are a Task Planner. Break the request into sub-tasks.
            DB: %s | Schema: ```json\n%s\n```
            %s %s %s
            Task types: explore | select | insert | update | delete

            CRITICAL RULE: For INSERT/UPDATE/DELETE requests, the FIRST task MUST be type "explore" to describe_table+query_data the target table. The actual write task depends on this explore task.

            Output JSON array: [{"type":"explore|select|insert|update|delete","target":"table","desc":"description","deps":""}]
            - deps: comma-separated descriptions of tasks this depends on (empty = independent)
            - "explore" tasks have NO deps and run first
            - Write tasks depend on their explore task
            - Independent tasks with different targets run in PARALLEL
            - Return ONLY the JSON array.
            """.formatted(dbType, schemaJson,
                tableName != null ? "Selected: " + tableName : "",
                config != null && config.getProjectBackground() != null && !config.getProjectBackground().isBlank()
                    ? "Project: " + config.getProjectBackground() : "",
                extra.toString());
    }

    private String buildExecutorPrompt(String dbType, String schemaJson, AiConfig config,
                                        String taskType, String taskTable, String taskDesc,
                                        Map<String, String> prevResults,
                                        String operationType, String intentType) {
        StringBuilder prev = new StringBuilder();
        if (!prevResults.isEmpty()) {
            prev.append("## Previous results\n");
            prevResults.forEach((k,v) -> prev.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        // Build intent-specific rules
        String intentRule = "";
        boolean skipExplore = false;
        switch (intentType) {
            case "query":
                skipExplore = true;
                intentRule = "QUERY mode. Skip exploration, generate SELECT directly. Only explore if table structure is unclear.";
                break;
            case "insert":
                intentRule = "INSERT mode. MUST explore first (describe_table + query_data to see columns & existing rows). Skip auto-increment PKs.";
                break;
            case "update":
                intentRule = "UPDATE mode. Explore describe_table+query_data TWICE at most, then MUST output [SQL:...]. Always include WHERE.";
                break;
            case "delete":
                intentRule = "DELETE mode. MUST explore first to see what will be deleted. Always include WHERE.";
                break;
            case "modify":
                intentRule = "MODIFY mode. Understand existing SQL issue. Explore data. Fix core problem without changing intent.";
                break;
            default:
                intentRule = "General mode. Explore if write, skip if query.";
        }
        if ("error_fix".equals(operationType)) {
            intentRule += " ERROR_FIX: Previous SQL failed. Do NOT repeat same mistake. Explore data to find correct approach.";
        }
        if ("human_suggestion".equals(operationType)) {
            intentRule += " HUMAN_SUGGESTION: A human gave specific advice. Follow it strictly.";
        }

        String workflowBlock = skipExplore ? """
            ╔══════════════════════════════════════╗
            ║  WORKFLOW (query mode):             ║
            ║  0. (OPTIONAL) describe_table       ║
            ║  1. OUTPUT: [SQL:SELECT ...]        ║
            ╚══════════════════════════════════════╝
            """ : """
            ╔══════════════════════════════════════╗
            ║  WORKFLOW (write mode):             ║
            ║  0. EXPLORE: describe_table+query   ║
            ║     to understand columns & data    ║
            ║  1. ANALYZE: what needs to change?  ║
            ║  2. OUTPUT: [SQL:...] pure SQL only ║
            ╚══════════════════════════════════════╝
            INSERT: skip auto-increment/PK columns. Use explored data to fill gaps.
            UPDATE: always include WHERE. Base conditions on explored data.
            DELETE: must have WHERE. Never delete without seeing data first.""";

        return """
            Execute ONE task. DB: %s | Task: %s | Table: %s
            Schema: ```json\n%s\n``` %s
            %s

            %s

            %s
            Output pure SQL — no markdown, no semicolons, no explanations, NO LIMIT.
            """.formatted(dbType, taskType, taskTable, schemaJson, prev.toString(),
                config != null && config.getProjectBackground() != null && !config.getProjectBackground().isBlank()
                    ? "Project: " + config.getProjectBackground() : "",
                intentRule,
                workflowBlock);
    }

    // ═══════════════════════════════════════════════════════
    //  PARSERS
    // ═══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseTasks(String plan) {
        try {
            String json = plan;
            if (json.contains("```json")) json = json.substring(json.indexOf("```json")+7);
            if (json.contains("```")) json = json.substring(0, json.lastIndexOf("```"));
            if (json.contains("[")) json = json.substring(json.indexOf("["));
            if (json.contains("]")) json = json.substring(0, json.lastIndexOf("]")+1);
            List<Map<String, Object>> raw = om.readValue(json, List.class);
            List<Map<String, String>> tasks = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                Map<String, String> t = new LinkedHashMap<>();
                t.put("type", String.valueOf(m.getOrDefault("type","auto")));
                t.put("target", String.valueOf(m.getOrDefault("target","")));
                t.put("desc", String.valueOf(m.getOrDefault("desc","")));
                t.put("deps", String.valueOf(m.getOrDefault("deps","")));
                tasks.add(t);
            }
            return tasks;
        } catch (Exception e) { return List.of(); }
    }

    private Map<String, String> parseParams(String s) {
        Map<String, String> p = new LinkedHashMap<>();
        for (String pair : s.split(",\\s*")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) p.put(kv[0].trim().replace("\"",""), kv[1].trim().replace("\"",""));
        }
        return p;
    }

    // ═══════════════════════════════════════════════════════
    //  AI CALL
    // ═══════════════════════════════════════════════════════

    private String callAI(AiConfig config, String system, String user, double temp, boolean extractFenced, int maxTokens) throws Exception {
        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", temp);
        body.put("max_tokens", maxTokens > 0 ? maxTokens : 4096);
        body.put("messages", List.of(
            Map.of("role","system","content",system),
            Map.of("role","user","content",user)
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(java.time.Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
            .build();

        HttpResponse<String> resp = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("API " + resp.statusCode() + ": " + resp.body());

        @SuppressWarnings("unchecked")
        Map<String, Object> rb = om.readValue(resp.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) rb.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("AI empty");
        String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
        return extractFenced ? cleanSQL(content) : content;
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════

    private AiConfig getConfig(String userId) { AiConfig c = configRepo.findByUserId(userId); if (c == null) throw new RuntimeException("请先配置AI"); return c; }

    private int outputTokens(AiConfig config) {
        int user = config.getMaxTokens() != null ? config.getMaxTokens() : 100000;
        return Math.max(user / 3, 16384);
    }
    private String getDbType(Long cid) { DbConnection c = connectionService.getConnection(cid); return c != null ? c.getDbType() : "postgresql"; }
    private String cleanSQL(String c) { if (c == null) return ""; return c.replaceAll("```sql\\s*","").replaceAll("```\\s*","").trim(); }

    /** Aggressive fallback: scan response for lines starting with SQL keywords */
    private String extractSQLLines(String text) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            String t = line.trim();
            String tu = t.toUpperCase();
            if (tu.startsWith("UPDATE ") || tu.startsWith("INSERT ") || tu.startsWith("SELECT ") ||
                tu.startsWith("DELETE ") || tu.startsWith("SET ")) {
                // Found a SQL-like line — grab it and following continuation lines
                StringBuilder sb = new StringBuilder(t);
                return sb.toString().trim();
            }
        }
        return null;
    }

    private String event(String type, Object data) { try { return "event: "+type+"\ndata: "+om.writeValueAsString(data)+"\n\n"; } catch (Exception e) { return ""; } }
}

// ═══════════════════════════════════════════════════════
//  SQL VALIDATOR — code-level rules (no AI)
// ═══════════════════════════════════════════════════════

class SqlValidator {
    private static final Set<String> SQL_STARTS = Set.of(
        "SELECT","INSERT","UPDATE","DELETE","CREATE","ALTER","DROP","TRUNCATE","EXPLAIN","WITH","BEGIN","COMMIT","ROLLBACK"
    );

    static boolean isSQL(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.trim().toUpperCase();
        // Must not start with description prefixes
        if (t.startsWith("STEP") || t.startsWith("步骤") || t.startsWith("TASK") || t.startsWith("任务")) return false;
        if (t.startsWith("//") || t.startsWith("--") || t.startsWith("#")) return false;
        // Must start with a SQL keyword
        for (String kw : SQL_STARTS) if (t.startsWith(kw)) return true;
        return false;
    }

    static Map<String, Object> check(String sql, String tableName, String dbType) {
        List<String> issues = new ArrayList<>();
        boolean pass = true;

        if (sql == null || sql.isBlank()) { issues.add("SQL为空"); pass = false; }
        else {
            String t = sql.trim();
            // 1. No markdown fences
            if (t.contains("```")) { issues.add("含Markdown代码块标记"); pass = false; }
            // 2. No trailing semicolons (single line with ; at end)
            String[] lines = t.split("\n");
            for (String line : lines) {
                if (line.trim().endsWith(";")) { issues.add("含分号结尾"); pass = false; break; }
            }
            // 3. Must start with SQL keyword
            if (!isSQL(t)) {
                String first = t.split("\n")[0].trim();
                if (first.length() > 60) first = first.substring(0, 60) + "...";
                issues.add("首行非SQL: " + first);
                pass = false;
            }
            // 4. INSERT must have VALUES
            String ut = t.toUpperCase();
            if (ut.startsWith("INSERT") && !ut.contains("VALUES")) {
                issues.add("INSERT缺少VALUES子句"); pass = false;
            }
            // 5. UPDATE must have SET
            if (ut.startsWith("UPDATE") && !ut.contains(" SET ")) {
                issues.add("UPDATE缺少SET子句"); pass = false;
            }
            // 6. No "Step" or description in SQL
            for (String line : lines) {
                String ul = line.trim().toUpperCase();
                if (ul.startsWith("STEP ") || ul.startsWith("步骤") || ul.startsWith("TASK ")) {
                    issues.add("SQL中包含步骤描述文字"); pass = false; break;
                }
            }
        }
        return Map.of("pass", pass, "issues", String.join("; ", issues));
    }
}

// ═══════════════════════════════════════════════════════
//  AI REVIEWER
// ═══════════════════════════════════════════════════════

class Reviewer {
    static Map<String, Object> run(AiConfig config, AESUtil aesUtil, ObjectMapper om,
                                    String dbType, String schemaJson, String sql,
                                    String task, String tableName) throws Exception {
        String prompt = """
            Review SQL. Return JSON: {"format":{"pass":bool,"reason":""},"logic":{"pass":bool,"reason":""},"safety":{"pass":bool,"reason":""},"overall":bool}
            DB: %s | Schema: ```json\n%s\n``` | SQL: %s | Task: %s | Target: %s
            """.formatted(dbType, schemaJson, sql, task, tableName != null ? tableName : "");

        String apiKey = aesUtil.decrypt(config.getApiKeyEncrypted());
        String baseUrl = config.getApiBaseUrl();
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("temperature", 0.1); body.put("max_tokens", 1024);
        body.put("messages", List.of(Map.of("role","system","content",prompt), Map.of("role","user","content","Return ONLY the JSON.")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(java.time.Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
            .build();

        HttpResponse<String> resp = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return Map.of("overall", true, "note", "review skipped");

        @SuppressWarnings("unchecked")
        Map<String, Object> rb = om.readValue(resp.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) rb.get("choices");
        if (choices == null || choices.isEmpty()) return Map.of("overall", true);
        String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

        try {
            String json = content;
            if (json.contains("{")) json = json.substring(json.indexOf("{"));
            if (json.contains("}")) json = json.substring(0, json.lastIndexOf("}")+1);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = om.readValue(json, Map.class);
            return result;
        } catch (Exception e) { return Map.of("overall", true, "note", "parse fallback"); }
    }
}
