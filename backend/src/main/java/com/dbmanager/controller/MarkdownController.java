package com.dbmanager.controller;

import com.dbmanager.entity.*;
import com.dbmanager.repository.MdChunkRepository;
import com.dbmanager.repository.MdDocumentRepository;
import com.dbmanager.service.MarkdownAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/markdown")
public class MarkdownController {

    private final MdDocumentRepository docRepo;
    private final MdChunkRepository chunkRepo;
    private final MarkdownAgent agent;
    private final ObjectMapper om = new ObjectMapper();

    public MarkdownController(MdDocumentRepository docRepo, MdChunkRepository chunkRepo, MarkdownAgent agent) {
        this.docRepo = docRepo; this.chunkRepo = chunkRepo; this.agent = agent;
    }

    private String userId(Authentication auth) { return auth != null ? auth.getName() : "anonymous"; }

    // ═══════════════════════════════════════════════════
    //  Upload
    // ═══════════════════════════════════════════════════

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, Authentication auth) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            MdDocument doc = new MdDocument();
            doc.setUserId(userId(auth));
            doc.setFileName(file.getOriginalFilename());
            doc.setFileSize(file.getSize());
            doc.setFullContent(content);
            doc.setStatus("uploaded");
            Long id = docRepo.save(doc);
            return ResponseEntity.ok(Map.of("success",true,"id",id,"fileName",doc.getFileName(),"fileSize",doc.getFileSize(),"status",doc.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error",e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<?> list(Authentication auth) {
        return ResponseEntity.ok(Map.of("documents", docRepo.findByUserId(userId(auth))));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        MdDocument doc = docRepo.findById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("id",doc.getId(),"fileName",doc.getFileName(),"fileSize",doc.getFileSize(),
            "fullContent",doc.getFullContent(),"aiSummary",doc.getAiSummary(),"status",doc.getStatus()));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        try {
            chunkRepo.deleteByDocumentId(id);
            docRepo.delete(id);
            return ResponseEntity.ok(Map.of("success",true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    //  SSE: Summarize
    // ═══════════════════════════════════════════════════

    @PostMapping("/summarize")
    public void summarize(@RequestBody Map<String, Object> req, Authentication auth, HttpServletResponse resp) throws Exception {
        Long id = Long.valueOf(req.get("id").toString());
        sseHeaders(resp);
        PrintWriter w = resp.getWriter();
        try {
            String summary = agent.generateSummary(id, evt -> writeSSE(w, evt));
            writeSSE(w, "event: done\ndata: " + om.writeValueAsString(Map.of("summary",summary)) + "\n\n");
        } catch (Exception e) { writeSSE(w, sseError(e)); } finally { w.close(); }
    }

    // ═══════════════════════════════════════════════════
    //  SSE: Chunking (returns proposals only, no DB save)
    // ═══════════════════════════════════════════════════

    @PostMapping("/chunk")
    public void chunk(@RequestBody Map<String, Object> req, Authentication auth, HttpServletResponse resp) throws Exception {
        Long id = Long.valueOf(req.get("id").toString());
        String tableName = (String) req.get("tableName");
        sseHeaders(resp);
        PrintWriter w = resp.getWriter();
        try {
            List<Map<String, Object>> rawChunks = agent.proposeChunks(id, tableName, evt -> writeSSE(w, evt));
            writeSSE(w, "event: done\ndata: " + om.writeValueAsString(Map.of("strategy", rawChunks, "total", rawChunks.size())) + "\n\n");
        } catch (Exception e) { writeSSE(w, sseError(e)); } finally { w.close(); }
    }

    // ═══════════════════════════════════════════════════
    //  Code-level slicing preview (no AI)
    // ═══════════════════════════════════════════════════

    @PostMapping("/slice")
    public ResponseEntity<?> slice(@RequestBody Map<String, Object> req) {
        try {
            Long id = Long.valueOf(req.get("id").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> strategy = (List<Map<String, Object>>) req.get("strategy");
            if (strategy == null || strategy.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "无分块策略"));
            Map<String, Object> result = agent.sliceChunksContent(id, strategy);
            return ResponseEntity.ok(Map.of("success", true, "structuredFile", result.get("structuredFile"),
                "semanticFile", result.get("semanticFile"),
                "structuredCount", result.get("structuredCount"), "semanticCount", result.get("semanticCount")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    //  Confirm & save to DB
    // ═══════════════════════════════════════════════════

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> req) {
        try {
            Long id = Long.valueOf(req.get("id").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> strategy = (List<Map<String, Object>>) req.get("strategy");
            if (strategy == null || strategy.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "无分块策略"));
            List<MdChunk> saved = agent.confirmAndSave(id, strategy);
            return ResponseEntity.ok(Map.of("success", true, "chunks", saved, "total", saved.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    //  Human feedback → AI re-chunking
    // ═══════════════════════════════════════════════════

    @PostMapping("/feedback")
    public void feedback(@RequestBody Map<String, Object> req, Authentication auth, HttpServletResponse resp) throws Exception {
        Long id = Long.valueOf(req.get("id").toString());
        String tableName = (String) req.get("tableName");
        String feedback = (String) req.get("feedback");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> currentStrategy = (List<Map<String, Object>>) req.get("strategy");

        sseHeaders(resp);
        PrintWriter w = resp.getWriter();
        try {
            List<Map<String, Object>> revised = agent.feedbackAndRechunk(id, tableName, currentStrategy, feedback, evt -> writeSSE(w, evt));
            writeSSE(w, "event: done\ndata: " + om.writeValueAsString(Map.of("strategy", revised, "total", revised.size())) + "\n\n");
        } catch (Exception e) { writeSSE(w, sseError(e)); } finally { w.close(); }
    }

    // ═══════════════════════════════════════════════════
    //  Export (from DB—requires confirmed chunks)
    // ═══════════════════════════════════════════════════

    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestBody Map<String, Object> req, Authentication auth) {
        try {
            Long id = Long.valueOf(req.get("id").toString());
            Map<String, String> files = agent.exportMarkdown(id);
            return ResponseEntity.ok(Map.of("success",true,
                "structuredFile",files.get("structuredFile"),
                "semanticFile",files.get("semanticFile"),
                "structuredFileName",structuredFileName(id, docRepo),
                "semanticFileName",semanticFileName(id, docRepo)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error",e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════
    //  Chunks CRUD
    // ═══════════════════════════════════════════════════

    @GetMapping("/chunks/{docId}")
    public ResponseEntity<?> getChunks(@PathVariable Long docId) {
        return ResponseEntity.ok(Map.of("chunks", chunkRepo.findByDocumentId(docId)));
    }

    @PutMapping("/chunks/{chunkId}")
    public ResponseEntity<?> updateChunk(@PathVariable Long chunkId, @RequestBody Map<String, Object> body) {
        String type = (String) body.get("chunkType");
        String title = (String) body.get("chunkTitle");
        chunkRepo.updateChunk(chunkId, type, title, true);
        return ResponseEntity.ok(Map.of("success",true));
    }

    // ═══════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════

    private void sseHeaders(HttpServletResponse resp) {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("X-Accel-Buffering", "no");
    }

    private void writeSSE(PrintWriter w, String data) {
        synchronized (w) { w.write(data); w.flush(); }
    }

    private String sseError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
        if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
        System.err.println("[Markdown ERROR] " + msg);
        e.printStackTrace(System.err);
        try {
            return "event: error\ndata: " + om.writeValueAsString(Map.of("message", msg)) + "\n\n";
        } catch (Exception ex) {
            return "event: error\ndata: {\"message\":\"SSE write failed\"}\n\n";
        }
    }

    private String structuredFileName(Long id, MdDocumentRepository repo) {
        MdDocument d = repo.findById(id);
        String base = d != null ? d.getFileName().replaceAll("\\.md$","") : "doc";
        return base + "_structured.md";
    }

    private String semanticFileName(Long id, MdDocumentRepository repo) {
        MdDocument d = repo.findById(id);
        String base = d != null ? d.getFileName().replaceAll("\\.md$","") : "doc";
        return base + "_semantic.md";
    }
}
