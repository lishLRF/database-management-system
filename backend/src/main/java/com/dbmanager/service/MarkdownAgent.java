package com.dbmanager.service;

import com.dbmanager.entity.*;
import com.dbmanager.repository.*;
import com.dbmanager.util.AESUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

@Service
public class MarkdownAgent {

    private final MdDocumentRepository docRepo;
    private final MdChunkRepository chunkRepo;
    private final AiConfigRepository configRepo;
    private final DynamicDataSourceService dataSourceService;
    private final AESUtil aesUtil;
    private final ObjectMapper om = new ObjectMapper();

    public MarkdownAgent(MdDocumentRepository docRepo, MdChunkRepository chunkRepo,
                         AiConfigRepository configRepo, DynamicDataSourceService dataSourceService,
                         AESUtil aesUtil) {
        this.docRepo = docRepo; this.chunkRepo = chunkRepo;
        this.configRepo = configRepo; this.dataSourceService = dataSourceService;
        this.aesUtil = aesUtil;
    }

    // ═══════════════════════════════════════════════════
    //  SUMMARY
    // ═══════════════════════════════════════════════════

    public String generateSummary(Long docId, Consumer<String> onEvent) throws Exception {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");
        AiConfig config = getConfig(doc.getUserId());

        onEvent.accept(event("phase", Map.of("phase","summarize","msg","AI 分析文档内容，生成摘要...")));

        String prompt = """
            请分析以下 Markdown 文档，生成一份摘要。摘要应包括：
            1. 文档主题和用途
            2. 主要章节/段落结构
            3. 包含的关键数据字段（如型号、参数、数值等）
            4. 可能涉及的数据库表结构建议

            文档内容：
            %s
            """.formatted(truncate(doc.getFullContent(), 30000));

        String summary = callAI(config, prompt, "Generate a concise summary in Chinese.", 0.3, outputTokens(config));
        docRepo.updateSummary(docId, summary, "summarized");
        onEvent.accept(event("summary_result", Map.of("summary", summary)));
        return summary;
    }

    // ═══════════════════════════════════════════════════
    //  MAIN PIPELINE: Phase 0a → 0b(AI) → 1 → 2 → 3
    // ═══════════════════════════════════════════════════

    public List<Map<String, Object>> proposeChunks(Long docId, String tableName, Consumer<String> onEvent) throws Exception {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");
        AiConfig config = getConfig(doc.getUserId());
        String fullContent = doc.getFullContent();

        // Get target table schema
        Map<String, Object> tableSchema = Map.of();
        Long connId = dataSourceService.getCurrentConnectionId();
        if (tableName != null && !tableName.isBlank() && connId != null) {
            try {
                Map<String, Object> schema = dataSourceService.getSchema(connId);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
                if (tables != null) {
                    for (Map<String, Object> t : tables) {
                        if (tableName.equals(t.get("name"))) { tableSchema = t; break; }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MarkdownAgent] Schema fetch failed: " + e.getMessage());
            }
        }

        onEvent.accept(event("phase", Map.of("phase","chunking","msg","开始智能分块分析...")));

        // ── Phase 0a: Code pre-scan ──
        onEvent.accept(event("step", Map.of("step","phase0a","msg","Phase 0a: 代码预扫 — 提取硬标题/候选标题/结构边界...")));
        MarkdownStructure structure = scanMarkdownStructure(fullContent);
        onEvent.accept(event("step", Map.of("step","phase0a_done","msg",
            String.format("预扫完成: 硬标题×%d, 候选×%d, 表格×%d, 代码块×%d",
                structure.hardHeadings.size(), structure.candidateHeadings.size(),
                structure.tableRanges.size(), structure.codeBlockRanges.size()))));

        // ── Phase 0b: AI validates candidate headings ──
        List<HeadingPos> validatedCandidates = List.of();
        if (!structure.candidateHeadings.isEmpty()) {
            onEvent.accept(event("step", Map.of("step","phase0b","msg",
                String.format("Phase 0b: AI 验证 %d 个候选标题...", structure.candidateHeadings.size()))));
            validatedCandidates = aiValidateCandidates(fullContent, structure, config, onEvent);
            onEvent.accept(event("step", Map.of("step","phase0b_done","msg",
                String.format("AI 确认: %d/%d 个候选为断义标题", validatedCandidates.size(), structure.candidateHeadings.size()))));
        }

        // Merge: hard headings + AI-validated candidates
        List<HeadingPos> allHeadings = new ArrayList<>();
        allHeadings.addAll(structure.hardHeadings);
        allHeadings.addAll(validatedCandidates);
        allHeadings.sort(Comparator.comparingInt(h -> h.lineStart));

        // ── Phase 1: Atomic split using all confirmed boundaries ──
        onEvent.accept(event("step", Map.of("step","phase1","msg","Phase 1: 原子拆分（使用全部验证边界）...")));
        List<Segment> segments = atomicSplit(fullContent, structure, allHeadings, config, onEvent);
        onEvent.accept(event("step", Map.of("step","phase1_done","msg",
            String.format("原子拆分: %d 个片段", segments.size()))));

        // ── Phase 2: Classify each segment ──
        onEvent.accept(event("step", Map.of("step","phase2","msg",
            String.format("Phase 2: 逐片段分类 (%d 个)...", segments.size()))));
        List<Map<String, Object>> chunks = classifySegments(segments, fullContent, tableSchema, tableName, config, onEvent);

        // ── Phase 3: Merge adjacent same-type + validate ──
        onEvent.accept(event("step", Map.of("step","phase3","msg","Phase 3: 相邻合并 + 验证...")));
        List<Map<String, Object>> merged = mergeAdjacentSameType(chunks, fullContent, onEvent);
        List<Map<String, Object>> validated = validateAndFix(merged, fullContent, onEvent);

        int structCount = (int) validated.stream().filter(c -> "structured_data".equals(c.get("type"))).count();
        int semCount = (int) validated.stream().filter(c -> "semantic_content".equals(c.get("type"))).count();
        onEvent.accept(event("step", Map.of("step","done","msg",
            String.format("完成: %d 个分块 (%d structured + %d semantic)", validated.size(), structCount, semCount))));

        onEvent.accept(event("chunk_result", Map.of("chunks", validated, "total", validated.size())));
        return validated;
    }

    // ════════════════════════════════════════════════════════════
    //  PHASE 0a: CODE PRE-SCAN
    // ════════════════════════════════════════════════════════════

    static class MarkdownStructure {
        List<HeadingPos> hardHeadings = new ArrayList<>();       // markdown #/## — always trusted
        List<HeadingPos> candidateHeadings = new ArrayList<>();  // need AI validation
        List<int[]> tableRanges = new ArrayList<>();
        List<int[]> codeBlockRanges = new ArrayList<>();
        List<int[]> paragraphBreaks = new ArrayList<>();
    }

    static class HeadingPos {
        int lineStart, contentStart, level;
        String title;
    }

    private MarkdownStructure scanMarkdownStructure(String content) {
        MarkdownStructure s = new MarkdownStructure();

        // ── Hard headings: markdown # / ## ──
        Matcher hm = Pattern.compile("(?m)^(#{1,2})\\s+(.+)$").matcher(content);
        while (hm.find()) {
            HeadingPos hp = new HeadingPos();
            hp.level = hm.group(1).length();
            hp.lineStart = hm.start();
            hp.contentStart = hm.end();
            hp.title = hm.group(2).trim();
            s.hardHeadings.add(hp);
        }

        // ── Candidate headings (need AI validation) ──

        // Chinese numbered: 一、 二、 1. 1.1 1.1.1 (一)
        Pattern chPattern = Pattern.compile(
            "(?m)^(?:([一二三四五六七八九十]+)[、．.]|" +
            "（([一二三四五六七八九十]+)）|" +
            "(\\d+(?:\\.\\d+)*)[、．.\\s]+)" +
            "(.+)$");
        Matcher chm = chPattern.matcher(content);
        while (chm.find()) {
            if (inBlock(s, chm.start())) continue;
            HeadingPos hp = new HeadingPos();
            hp.level = -1; // mark as candidate
            hp.lineStart = chm.start();
            hp.contentStart = chm.end();
            hp.title = chm.group(0).trim();
            if (hp.title.length() < 150) s.candidateHeadings.add(hp);
        }

        // Bold-only lines: **text** or __text__
        Pattern boldPattern = Pattern.compile("(?m)^(\\*\\*|__)([^*_\\n]{2,80})(\\*\\*|__)\\s*$");
        Matcher bm = boldPattern.matcher(content);
        while (bm.find()) {
            if (inBlock(s, bm.start())) continue;
            HeadingPos hp = new HeadingPos();
            hp.level = -1;
            hp.lineStart = bm.start();
            hp.contentStart = bm.end();
            hp.title = bm.group(2).trim();
            s.candidateHeadings.add(hp);
        }

        // ── Sort ──
        s.hardHeadings.sort(Comparator.comparingInt(h -> h.lineStart));
        s.candidateHeadings.sort(Comparator.comparingInt(h -> h.lineStart));

        // ── Tables ──
        Matcher tm = Pattern.compile("(?m)^\\|.+\\|$").matcher(content);
        List<Integer> tableLines = new ArrayList<>();
        while (tm.find()) tableLines.add(tm.start());
        for (int i = 0; i < tableLines.size(); i++) {
            int segStart = tableLines.get(i);
            int segEnd = content.indexOf('\n', segStart);
            if (segEnd < 0) segEnd = content.length() - 1;
            while (i + 1 < tableLines.size() && tableLines.get(i + 1) - segEnd < 300) {
                i++;
                segEnd = content.indexOf('\n', tableLines.get(i));
                if (segEnd < 0) segEnd = content.length() - 1;
            }
            s.tableRanges.add(new int[]{segStart, Math.min(segEnd + 1, content.length())});
        }

        // ── Code blocks ──
        Matcher cm = Pattern.compile("```[^\\n]*\\n").matcher(content);
        while (cm.find()) {
            int blockEnd = content.indexOf("\n```", cm.end());
            if (blockEnd < 0) blockEnd = content.length();
            else blockEnd += 4;
            s.codeBlockRanges.add(new int[]{cm.start(), Math.min(blockEnd, content.length())});
        }

        // ── Paragraph breaks ──
        Matcher pm = Pattern.compile("\\n\\n+").matcher(content);
        while (pm.find()) s.paragraphBreaks.add(new int[]{pm.start(), pm.end()});

        return s;
    }

    private boolean inBlock(MarkdownStructure s, int pos) {
        for (int[] cb : s.codeBlockRanges) if (pos >= cb[0] && pos < cb[1]) return true;
        for (int[] tr : s.tableRanges) if (pos >= tr[0] && pos < tr[1]) return true;
        return false;
    }

    // ════════════════════════════════════════════════════════════
    //  PHASE 0b: AI VALIDATES CANDIDATE HEADINGS
    // ════════════════════════════════════════════════════════════

    private List<HeadingPos> aiValidateCandidates(String fullContent, MarkdownStructure structure,
                                                   AiConfig config, Consumer<String> onEvent) throws Exception {
        if (structure.candidateHeadings.isEmpty()) return List.of();

        // Build a batch prompt: send all candidates with surrounding context
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是技术文档结构分析专家。下列是从文档中提取的**候选标题**，请判断每个是否为"语义断义点"。

            ## 判断标准
            - ✅ 是标题：该行是章节/小节的开头，后续内容围绕该主题展开
            - ❌ 不是标题：该行只是强调文字、列表项、表格内容、或普通句子

            ## 候选列表（含上下文）
            """);

        for (int i = 0; i < structure.candidateHeadings.size(); i++) {
            HeadingPos h = structure.candidateHeadings.get(i);
            // Extract context: 80 chars before + candidate line + 120 chars after
            int ctxStart = Math.max(0, h.lineStart - 80);
            int ctxEnd = Math.min(fullContent.length(), h.contentStart + 120);
            String before = fullContent.substring(ctxStart, h.lineStart).replace("\n", "\\n");
            String line = fullContent.substring(h.lineStart, Math.min(h.contentStart, fullContent.length())).replace("\n", "\\n");
            String after = fullContent.substring(h.contentStart, ctxEnd).replace("\n", "\\n");

            sb.append(String.format("""
                ---
                候选 %d: `%s`
                上文: "%s"
                下文: "%s"
                """, i + 1, line, before, after));
        }

        sb.append("""

            返回 JSON 数组，列出**确认为标题的候选编号**（1-based）:
            [1, 4, 7, ...]

            只返回 JSON 数组，不要其他文字。
            """);

        String response = callAI(config, sb.toString(), "Validate heading candidates.", 0.1, toolLoopTokens(config));

        // Parse response: [1, 4, 7, ...]
        try {
            String json = extractJSON(response);
            @SuppressWarnings("unchecked")
            List<Integer> confirmed = om.readValue(json, List.class);
            List<HeadingPos> result = new ArrayList<>();
            for (Object idxObj : confirmed) {
                int idx = ((Number) idxObj).intValue() - 1; // 1-based → 0-based
                if (idx >= 0 && idx < structure.candidateHeadings.size()) {
                    HeadingPos h = structure.candidateHeadings.get(idx);
                    h.level = 2; // confirmed as heading
                    result.add(h);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("[MarkdownAgent] Candidate validation parse error: " + e.getMessage());
            System.err.println("[MarkdownAgent] Raw: " + truncate(response, 300));
            // Fallback: accept all Chinese-numbered, reject bold
            List<HeadingPos> fallback = new ArrayList<>();
            for (HeadingPos h : structure.candidateHeadings) {
                if (h.title.matches("^[一二三四五六七八九十\\d].*")) {
                    h.level = 2;
                    fallback.add(h);
                }
            }
            return fallback;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  PHASE 1: ATOMIC SPLIT — no count limit
    // ════════════════════════════════════════════════════════════

    static class Segment {
        int start, end;
        String title;
        Segment(int s, int e, String t) { start = s; end = e; title = t; }
        int length() { return end - start; }
    }

    private List<Segment> atomicSplit(String fullContent, MarkdownStructure structure,
                                       List<HeadingPos> allHeadings, AiConfig config,
                                       Consumer<String> onEvent) throws Exception {
        // Build split points from ALL structural boundaries
        TreeSet<Integer> splitPoints = new TreeSet<>();
        splitPoints.add(0);
        splitPoints.add(fullContent.length());

        for (HeadingPos h : allHeadings) splitPoints.add(h.lineStart);
        for (int[] tr : structure.tableRanges) { splitPoints.add(tr[0]); splitPoints.add(tr[1]); }
        for (int[] cb : structure.codeBlockRanges) { splitPoints.add(cb[0]); splitPoints.add(cb[1]); }
        for (int[] pb : structure.paragraphBreaks) splitPoints.add(pb[0]);

        // Create segments — skip only < 20 char noise fragments
        List<Integer> sorted = new ArrayList<>(splitPoints);
        List<Segment> segments = new ArrayList<>();

        for (int i = 0; i < sorted.size() - 1; i++) {
            int s = sorted.get(i), e = sorted.get(i + 1);
            if (e - s < 20) continue;

            // Find nearest heading title
            String title = "未命名";
            for (HeadingPos h : allHeadings) {
                if (h.lineStart <= s) title = h.title;
                else break;
            }

            String text = fullContent.substring(s, e).trim();
            if (!text.isEmpty()) segments.add(new Segment(s, e, title));
        }

        onEvent.accept(event("step", Map.of("step","phase1_boundaries","msg",
            String.format("边界拆分: %d 个片段", segments.size()))));

        // AI-split any segment > 3000 chars (no count limit on result)
        List<Segment> finalSegments = new ArrayList<>();
        int aiSplitCount = 0;
        for (Segment seg : segments) {
            if (seg.length() <= 3000) {
                finalSegments.add(seg);
            } else {
                onEvent.accept(event("step", Map.of("step","phase1_aisplit","msg",
                    String.format("AI拆分超长段 '%s' (%d字符)...", seg.title, seg.length()))));
                List<Segment> subs = aiAtomicSplit(fullContent, seg.start, seg.end, seg.title, config);
                finalSegments.addAll(subs);
                aiSplitCount++;
                onEvent.accept(event("step", Map.of("step","phase1_aisplit_done","msg",
                    String.format("  → %d 个子片段", subs.size()))));
            }
        }
        if (aiSplitCount > 0) {
            onEvent.accept(event("step", Map.of("step","phase1_ai_summary","msg",
                String.format("AI处理 %d 个超长段", aiSplitCount))));
        }

        return finalSegments;
    }

    /** AI splits one oversized segment — no target size arg, uses adaptive sizing */
    private List<Segment> aiAtomicSplit(String fullContent, int segStart, int segEnd,
                                         String parentTitle, AiConfig config) throws Exception {
        String text = fullContent.substring(segStart, segEnd);
        if (text.length() < 500) return List.of(new Segment(segStart, segEnd, parentTitle));

        String prompt = """
            将以下文本切分为原子信息片段。

            ## 规则
            1. 每个片段应是**单一主题**（一个数据表、一段描述、一个公式）
            2. 在语义边界切分（标题、话题变化、段落切换、表格开始/结束）
            3. 表格区域单独划为一个片段
            4. 片段数量不限，按内容自然粒度切分
            5. 必须完整覆盖全部文本，不能有遗漏

            ## 文本（%d 字符）
            %s

            返回 JSON 数组（仅此，不要其他文字）:
            [{"start":0,"end":256,"title":"片段名"}, ...]
            start/end 是相对于上述文本的字符位置（0起始）。start 从 0 开始，最后一个 end 必须等于 %d。
            """.formatted(text.length(), truncate(text, 15000), text.length());

        String response = callAI(config, prompt, "Split text into atomic segments.", 0.3, outputTokens(config));
        return parseAtomicSplitResponse(response, text, segStart);
    }

    private List<Segment> parseAtomicSplitResponse(String response, String sectionText, int offset) {
        try {
            String json = extractJSON(response);
            @SuppressWarnings("unchecked")
            List<Object> raw = om.readValue(json, List.class);
            List<Segment> result = new ArrayList<>();
            int cursor = 0;
            for (Object o : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                int start = ((Number) m.get("start")).intValue();
                int end = ((Number) m.get("end")).intValue();
                String title = (String) m.getOrDefault("title", "片段");
                if (start > cursor) {
                    result.add(new Segment(offset + cursor, offset + start, "未命名片段"));
                }
                if (start < end && end <= sectionText.length()) {
                    result.add(new Segment(offset + start, offset + end, title));
                }
                cursor = end;
            }
            if (cursor < sectionText.length()) {
                result.add(new Segment(offset + cursor, offset + sectionText.length(), "尾部片段"));
            }
            return result.isEmpty()
                ? List.of(new Segment(offset, offset + sectionText.length(), "全文拆分失败"))
                : result;
        } catch (Exception e) {
            System.err.println("[MarkdownAgent] Atomic split parse error: " + e.getMessage());
            return List.of(new Segment(offset, offset + sectionText.length(), "全文(解析失败)"));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  PHASE 2: SIMPLE CLASSIFY
    // ════════════════════════════════════════════════════════════

    private List<Map<String, Object>> classifySegments(List<Segment> segments, String fullContent,
                                                        Map<String, Object> tableSchema, String tableName,
                                                        AiConfig config, Consumer<String> onEvent) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Set<Integer> processed = new HashSet<>();
        int toolCalls = 0;

        StringBuilder conv = new StringBuilder();
        conv.append(buildClassifierSystemPrompt(tableSchema, tableName)).append("\n\n");
        conv.append("## 待分类片段（共").append(segments.size()).append("个）\n");
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            String preview = fullContent.substring(seg.start, Math.min(seg.end, seg.start + 200))
                .replace("\n", "\\n");
            conv.append(String.format("%d. [%d-%d] %s | %s\n", i + 1, seg.start, seg.end, seg.title, preview));
        }
        conv.append("\n按顺序处理，每次一个工具调用。全部完成后 [TOOL:done{}]。");

        int maxIter = Math.min(segments.size() * 2 + 8, 80);
        for (int iter = 0; iter < maxIter; iter++) {
            if (processed.size() >= segments.size()) break;

            String prompt = conv.toString();
            if (iter > 0) prompt += "\n\n---\n继续处理剩余片段。";

            String response = callAI(config, prompt, "Classify each segment.", 0.15, toolLoopTokens(config));

            List<ToolCall> calls = extractToolCalls(response);
            if (calls.isEmpty()) {
                if (processed.size() >= segments.size()) break;
                conv.append("\n[请输出工具调用: classify_segment 或 skip_segment 或 done]\n");
                continue;
            }

            for (ToolCall call : calls) {
                toolCalls++;
                Map<String, Object> result = executeTool(call, segments, fullContent, processed, results);
                String resultStr = om.writeValueAsString(result);

                onEvent.accept(event("tool_call", Map.of(
                    "step", toolCalls, "tool", call.name, "params", call.params,
                    "preview", resultStr.substring(0, Math.min(150, resultStr.length())))));

                conv.append("\n[TOOL RESULT ").append(call.name).append("] ").append(resultStr).append("\n");

                if ("done".equals(call.name)) {
                    for (int i = 0; i < segments.size(); i++) {
                        if (!processed.contains(i)) {
                            Segment seg = segments.get(i);
                            results.add(chunkFromSegment(seg, "semantic_content", fullContent, "自动归为语义"));
                        }
                    }
                    break;
                }
            }
            if (calls.stream().anyMatch(c -> "done".equals(c.name))) break;
        }

        onEvent.accept(event("step", Map.of("step","phase2_done","msg",
            String.format("分类完成: %d 分块, %d 次工具调用", results.size(), toolCalls))));
        return results;
    }

    private String buildClassifierSystemPrompt(Map<String, Object> tableSchema, String tableName) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            ## 角色
            数据库ETL工程师。对每个原子片段分类。

            ## 分类标准
            - structured_data: 可映射到数据库表的行列数据（参数表、规格表、清单、测量数值）
              → 有表格(|...|)、键值对(参数名:数值)、列表式数据
            - semantic_content: 描述性文字、公式推导、经验规则、背景说明、标题

            ## 工具
            [TOOL:classify_segment{"start":0,"end":256,"type":"structured_data","title":"片段名","reason":"含参数表"}]
            [TOOL:skip_segment{"start":0,"end":50,"reason":"目录/空白"}]
            [TOOL:done{}]

            ## 规则
            1. 按编号顺序逐个处理
            2. 每次只输出一个工具调用，不输出解释
            3. 有表格(|...|) → structured_data
            4. 标题行/目录行 → skip_segment
            5. 处理完所有片段 → done
            """);

        if (tableName != null && !tableSchema.isEmpty()) {
            sb.append("\n## 参考表: ").append(tableName).append("\n```json\n")
              .append(om.valueToTree(tableSchema)).append("\n```\n");
        }
        return sb.toString();
    }

    static class ToolCall { String name; String params; }

    private List<ToolCall> extractToolCalls(String text) {
        List<ToolCall> calls = new ArrayList<>();
        Matcher tm = Pattern.compile("\\[TOOL:(\\w+)\\{([^}]*)\\}\\]").matcher(text);
        while (tm.find()) {
            ToolCall tc = new ToolCall();
            tc.name = tm.group(1);
            tc.params = tm.group(2);
            calls.add(tc);
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeTool(ToolCall call, List<Segment> segments,
                                             String fullContent, Set<Integer> processed,
                                             List<Map<String, Object>> results) {
        try {
            String jsonStr = "{" + call.params + "}";
            Map<String, Object> p = om.readValue(jsonStr, Map.class);

            return switch (call.name) {
                case "classify_segment" -> {
                    int start = ((Number) p.get("start")).intValue();
                    int end = ((Number) p.get("end")).intValue();
                    String type = (String) p.getOrDefault("type", "semantic_content");
                    String title = (String) p.getOrDefault("title", "片段");
                    String reason = (String) p.getOrDefault("reason", "");

                    if (!"structured_data".equals(type) && !"semantic_content".equals(type))
                        yield Map.of("error", "type必须是 structured_data 或 semantic_content");

                    int idx = -1;
                    for (int i = 0; i < segments.size(); i++)
                        if (segments.get(i).start == start && segments.get(i).end == end) { idx = i; break; }
                    if (idx < 0)
                        yield Map.of("error", "片段["+start+"-"+end+"]不存在");

                    processed.add(idx);
                    if (start >= end) yield Map.of("error", "start>=end");

                    Segment seg = segments.get(idx);
                    Map<String, Object> chunk = new LinkedHashMap<>();
                    chunk.put("start", seg.start); chunk.put("end", seg.end);
                    chunk.put("title", title); chunk.put("type", type);
                    chunk.put("reason", reason);
                    chunk.put("content", fullContent.substring(seg.start, seg.end));
                    results.add(chunk);
                    yield Map.of("status","ok","classified_as",type,"title",title,"total",results.size());
                }
                case "skip_segment" -> {
                    int start = ((Number) p.get("start")).intValue();
                    int end = ((Number) p.get("end")).intValue();
                    for (int i = 0; i < segments.size(); i++)
                        if (segments.get(i).start == start && segments.get(i).end == end) { processed.add(i); break; }
                    yield Map.of("status","skipped");
                }
                case "done" -> Map.of("status","done");
                default -> Map.of("error","未知工具: "+call.name);
            };
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> chunkFromSegment(Segment seg, String type, String fullContent, String reason) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("start", seg.start); c.put("end", seg.end);
        c.put("title", seg.title); c.put("type", type);
        c.put("reason", reason);
        c.put("content", fullContent.substring(seg.start, seg.end));
        return c;
    }

    // ════════════════════════════════════════════════════════════
    //  PHASE 3: MERGE + VALIDATE
    // ════════════════════════════════════════════════════════════

    private List<Map<String, Object>> mergeAdjacentSameType(List<Map<String, Object>> chunks,
                                                             String fullContent, Consumer<String> onEvent) {
        if (chunks.size() <= 1) return chunks;
        chunks.sort(Comparator.comparingInt(c -> ((Number) c.get("start")).intValue()));

        List<Map<String, Object>> merged = new ArrayList<>();
        Map<String, Object> current = null;
        int mergeCount = 0;

        for (Map<String, Object> next : chunks) {
            if (current == null) { current = new LinkedHashMap<>(next); continue; }

            boolean sameType = current.get("type").equals(next.get("type"));
            int gap = ((Number) next.get("start")).intValue() - ((Number) current.get("end")).intValue();
            boolean adjacent = gap >= 0 && gap < 200;

            if (sameType && adjacent) {
                current.put("end", ((Number) next.get("end")).intValue());
                current.put("content", fullContent.substring(
                    ((Number) current.get("start")).intValue(),
                    ((Number) next.get("end")).intValue()));
                current.put("reason", current.getOrDefault("reason", "") + "; +" + next.get("title"));
                mergeCount++;
            } else {
                merged.add(current);
                current = new LinkedHashMap<>(next);
            }
        }
        if (current != null) merged.add(current);

        if (mergeCount > 0) {
            onEvent.accept(event("step", Map.of("step","merge","msg",
                String.format("合并 %d 对相邻同类型 → %d 个分块", mergeCount, merged.size()))));
        }
        return merged;
    }

    private List<Map<String, Object>> validateAndFix(List<Map<String, Object>> chunks, String fullContent,
                                                      Consumer<String> onEvent) {
        int fixCount = 0;
        List<Map<String, Object>> working = new ArrayList<>(chunks);
        working.sort(Comparator.comparingInt(c -> ((Number) c.get("start")).intValue()));

        // D1: Coverage
        int cursor = 0;
        List<Map<String, Object>> withGaps = new ArrayList<>();
        for (Map<String, Object> c : working) {
            int s = ((Number) c.get("start")).intValue();
            int e = ((Number) c.get("end")).intValue();
            if (s > cursor) {
                String gapContent = fullContent.substring(cursor, s).trim();
                if (!gapContent.isEmpty()) {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("start", cursor); gap.put("end", s);
                    gap.put("title", "未分类"); gap.put("type", "semantic_content");
                    gap.put("reason", "自动补全");
                    gap.put("content", fullContent.substring(cursor, s));
                    withGaps.add(gap);
                    fixCount++;
                }
            }
            withGaps.add(c);
            cursor = Math.max(cursor, e);
        }
        if (cursor < fullContent.length()) {
            String tail = fullContent.substring(cursor).trim();
            if (!tail.isEmpty()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("start", cursor); t.put("end", fullContent.length());
                t.put("title", "末尾"); t.put("type", "semantic_content");
                t.put("reason", "自动补全");
                t.put("content", fullContent.substring(cursor));
                withGaps.add(t); fixCount++;
            }
        }
        working = withGaps;

        // D2: Overlap
        for (int i = 0; i < working.size() - 1; i++) {
            Map<String, Object> a = working.get(i), b = working.get(i + 1);
            int aEnd = ((Number) a.get("end")).intValue();
            int bStart = ((Number) b.get("start")).intValue();
            if (aEnd > bStart) {
                a.put("end", bStart);
                a.put("content", fullContent.substring(((Number) a.get("start")).intValue(), bStart));
                fixCount++;
            }
        }

        // D3: Boundary snap
        for (Map<String, Object> c : working) {
            int start = ((Number) c.get("start")).intValue();
            int end = ((Number) c.get("end")).intValue();
            int ns = snapToBoundary(fullContent, start, 50);
            int ne = snapToBoundary(fullContent, end, 50);
            if (ns != start || ne != end) { c.put("start", ns); c.put("end", ne); fixCount++; }
        }

        // D5: Too-small (warn only)
        for (Map<String, Object> c : working) {
            String ct = (String) c.get("content");
            if (ct != null && ct.trim().length() < 20) {
                onEvent.accept(event("step", Map.of("step","validate_small","msg",
                    String.format("过小块: '%s' (%d字符)", c.get("title"), ct.trim().length()))));
            }
        }

        if (fixCount == 0) onEvent.accept(event("step", Map.of("step","validate_clean","msg","验证通过 ✅")));
        return working;
    }

    private int snapToBoundary(String content, int pos, int maxDist) {
        if (pos <= 0 || pos >= content.length()) return pos;
        int best = pos, bestDist = maxDist + 1;
        int lo = Math.max(0, pos - maxDist), hi = Math.min(content.length() - 1, pos + maxDist);
        for (int i = lo; i < hi; i++) {
            if (content.charAt(i) == '\n' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                int d = Math.abs(i - pos);
                if (d < bestDist) { bestDist = d; best = i; }
            }
        }
        return best;
    }

    // ════════════════════════════════════════════════════════════
    //  SLICE / CONFIRM / FEEDBACK / EXPORT
    // ════════════════════════════════════════════════════════════

    public Map<String, Object> sliceChunksContent(Long docId, List<Map<String, Object>> strategy) {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");
        String fullContent = doc.getFullContent();

        StringBuilder structured = new StringBuilder(), semantic = new StringBuilder();
        int structCount = 0, semCount = 0;
        Integer prevStructEnd = null, prevSemEnd = null;

        List<Map<String, Object>> sorted = new ArrayList<>(strategy);
        sorted.sort(Comparator.comparingInt(c -> ((Number) c.get("start")).intValue()));

        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> s = sorted.get(i);
            String type = (String) s.getOrDefault("type", "semantic_content");
            int start = ((Number) s.get("start")).intValue();
            int end = Math.min(((Number) s.get("end")).intValue(), fullContent.length());
            String content = fullContent.substring(start, end);

            StringBuilder target;
            Integer prevEnd;
            if ("structured_data".equals(type)) { target = structured; prevEnd = prevStructEnd; }
            else { target = semantic; prevEnd = prevSemEnd; }

            if (prevEnd != null && start > prevEnd) {
                String other = "structured_data".equals(type) ? "语义向量化数据分块文档" : "结构化数据分块文档";
                target.append("\n> 中间块 处于 ").append(other).append(" 中\n\n");
            }

            String title = (String) s.getOrDefault("title", "块" + (i + 1));
            target.append("## 块").append(i + 1).append(": ").append(title).append("\n\n");
            target.append(content).append("\n\n");

            if ("structured_data".equals(type)) { prevStructEnd = end; structCount++; }
            else { prevSemEnd = end; semCount++; }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("structuredFile", structured.toString());
        result.put("semanticFile", semantic.toString());
        result.put("structuredCount", structCount);
        result.put("semanticCount", semCount);
        return result;
    }

    public List<MdChunk> confirmAndSave(Long docId, List<Map<String, Object>> strategy) {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");

        chunkRepo.deleteByDocumentId(docId);
        List<MdChunk> result = new ArrayList<>();
        String fullContent = doc.getFullContent();

        List<Map<String, Object>> sorted = new ArrayList<>(strategy);
        sorted.sort(Comparator.comparingInt(c -> ((Number) c.get("start")).intValue()));

        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> s = sorted.get(i);
            MdChunk chunk = new MdChunk();
            chunk.setDocumentId(docId);
            chunk.setChunkIndex(i + 1);
            chunk.setChunkType((String) s.getOrDefault("type", "semantic_content"));
            chunk.setChunkTitle((String) s.getOrDefault("title", "块" + (i + 1)));
            int start = ((Number) s.get("start")).intValue();
            int end = Math.min(((Number) s.get("end")).intValue(), fullContent.length());
            chunk.setContentStartPos(start);
            chunk.setContentEndPos(end);
            chunk.setChunkContent(fullContent.substring(start, end));
            chunk.setClassificationReason((String) s.getOrDefault("reason", ""));
            chunk.setHumanModified(false);
            chunkRepo.save(chunk);
            result.add(chunk);
        }
        docRepo.updateStatus(docId, "classified");
        return result;
    }

    public List<Map<String, Object>> feedbackAndRechunk(
            Long docId, String tableName, List<Map<String, Object>> currentStrategy,
            String humanFeedback, Consumer<String> onEvent) throws Exception {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");
        AiConfig config = getConfig(doc.getUserId());

        onEvent.accept(event("phase", Map.of("phase","rechunk","msg","根据反馈调整分块策略...")));

        Map<String, Object> tableSchema = Map.of();
        Long connId = dataSourceService.getCurrentConnectionId();
        if (tableName != null && !tableName.isBlank() && connId != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tables = (List<Map<String, Object>>) dataSourceService.getSchema(connId).get("tables");
                if (tables != null) for (var t : tables) if (tableName.equals(t.get("name"))) { tableSchema = t; break; }
            } catch (Exception e) { System.err.println("[MarkdownAgent] Schema fetch failed: " + e.getMessage()); }
        }

        String prompt = buildClassifierSystemPrompt(tableSchema, tableName) + "\n\n" + """
            ## 当前分块
            %s

            ## 反馈
            %s

            基于反馈调整。只输出需修改片段的 classify_segment/skip_segment，最后 [TOOL:done{}]。
            """.formatted(om.valueToTree(currentStrategy).toString(), humanFeedback);

        String response = callAI(config, prompt, "Revise chunks.", 0.2, outputTokens(config));
        return parseChunkResponse(response, doc.getFullContent());
    }

    public Map<String, String> exportMarkdown(Long docId) throws Exception {
        MdDocument doc = docRepo.findById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");

        List<MdChunk> chunks = chunkRepo.findByDocumentId(docId);
        if (chunks.isEmpty()) throw new RuntimeException("尚未分块");

        StringBuilder structured = new StringBuilder(), semantic = new StringBuilder();
        structured.append("# 结构化数据分块\n> 源文件: ").append(doc.getFileName()).append("\n\n");
        semantic.append("# 语义向量化内容分块\n> 源文件: ").append(doc.getFileName()).append("\n\n");

        MdChunk prevStructured = null, prevSemantic = null;
        for (MdChunk chunk : chunks) {
            StringBuilder target = chunk.getChunkType().equals("structured_data") ? structured : semantic;
            MdChunk prev = chunk.getChunkType().equals("structured_data") ? prevStructured : prevSemantic;
            if (prev != null && chunk.getContentStartPos() > prev.getContentEndPos()) {
                String other = chunk.getChunkType().equals("structured_data") ? "语义向量化数据分块文档" : "结构化数据分块文档";
                target.append("\n> 中间块 处于 ").append(other).append(" 中\n\n");
            }
            target.append("## 块").append(chunk.getChunkIndex()).append(": ").append(chunk.getChunkTitle()).append("\n\n");
            target.append(chunk.getChunkContent()).append("\n\n");
            if (chunk.getChunkType().equals("structured_data")) prevStructured = chunk;
            else prevSemantic = chunk;
        }
        docRepo.updateStatus(docId, "exported");
        return Map.of("structuredFile", structured.toString(), "semanticFile", semantic.toString());
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private AiConfig getConfig(String userId) {
        AiConfig c = configRepo.findByUserId(userId);
        if (c == null) throw new RuntimeException("请先配置AI");
        return c;
    }

    private int outputTokens(AiConfig config) {
        int user = config.getMaxTokens() != null ? config.getMaxTokens() : 100000;
        return Math.max(user / 3, 16384);
    }

    private int toolLoopTokens(AiConfig config) {
        return Math.min(outputTokens(config), 16384);
    }

    private String callAI(AiConfig config, String system, String user, double temp, int maxTokens) throws Exception {
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
        if (resp.statusCode() != 200) throw new RuntimeException("API " + resp.statusCode() + ": " + truncate(resp.body(), 200));

        @SuppressWarnings("unchecked")
        Map<String, Object> rb = om.readValue(resp.body(), Map.class);
        List<Object> choices = (List<Object>) rb.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("AI empty response");

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
        String content = (String) message.get("content");
        String reasoning = (String) message.get("reasoning_content");

        if (content == null || content.isBlank()) {
            if (reasoning != null && !reasoning.isBlank()) {
                System.err.println("[MarkdownAgent] content empty, falling back to reasoning (" + reasoning.length() + " chars)");
                String extracted = extractFinalFromReasoning(reasoning);
                if (extracted != null && !extracted.isBlank()) return extracted;
                return reasoning.substring(Math.max(0, reasoning.length() - 2000));
            }
            System.err.println("[MarkdownAgent] AI empty. Body: " + truncate(resp.body(), 1000));
            throw new RuntimeException("AI returned empty response. Possibly max_tokens too small.");
        }
        return content;
    }

    private String extractFinalFromReasoning(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) return null;
        Matcher tm = Pattern.compile("\\[TOOL:\\w+\\{[^}]*\\}\\]").matcher(reasoning);
        StringBuilder tools = new StringBuilder();
        while (tm.find()) tools.append(tm.group()).append("\n");
        if (!tools.isEmpty()) return tools.toString().trim();
        int jsonStart = reasoning.lastIndexOf("[{");
        if (jsonStart >= 0) {
            int jsonEnd = reasoning.indexOf("}]", jsonStart);
            if (jsonEnd >= 0) return reasoning.substring(jsonStart, jsonEnd + 2);
        }
        return reasoning.substring(Math.max(0, reasoning.length() - 1500));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseChunkResponse(String response, String fullContent) {
        try {
            String json = extractJSON(response);
            List<Object> raw = om.readValue(json, List.class);
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (Object o : raw) {
                Map<String, Object> m = (Map<String, Object>) o;
                int start = ((Number) m.get("start")).intValue();
                int end = ((Number) m.get("end")).intValue();
                if (start < 0) start = 0;
                if (end > fullContent.length()) end = fullContent.length();
                m.put("content", fullContent.substring(start, end));
                chunks.add(m);
            }
            return chunks;
        } catch (Exception e) {
            System.err.println("[MarkdownAgent] Parse error: " + e.getMessage());
            System.err.println("[MarkdownAgent] Raw: " + truncate(response, 500));
            return List.of();
        }
    }

    private String extractJSON(String text) {
        String t = text;
        if (t.contains("```json")) t = t.substring(t.indexOf("```json") + 7);
        if (t.contains("```")) t = t.substring(0, t.lastIndexOf("```"));
        if (t.contains("[")) t = t.substring(t.indexOf("["));
        if (t.contains("]")) t = t.substring(0, t.lastIndexOf("]") + 1);
        return t.trim();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "\n...(truncated)" : s;
    }

    private String event(String type, Object data) {
        try { return "event: " + type + "\ndata: " + om.writeValueAsString(data) + "\n\n"; }
        catch (Exception e) { return ""; }
    }
}
