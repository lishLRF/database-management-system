package com.dbmanager.controller;

import com.dbmanager.entity.SqlSnippet;
import com.dbmanager.repository.SqlSnippetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sql-snippets")
public class SqlSnippetController {

    private final SqlSnippetRepository snippetRepo;

    public SqlSnippetController(SqlSnippetRepository snippetRepo) {
        this.snippetRepo = snippetRepo;
    }

    private String userId(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String search, Authentication auth) {
        String uid = userId(auth);
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(snippetRepo.searchByUserId(uid, search.trim()));
        }
        return ResponseEntity.ok(snippetRepo.findByUserId(uid));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        String uid = userId(auth);
        String title = body.get("title");
        String sqlContent = body.get("sqlContent");
        if (title == null || title.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "标题不能为空"));
        if (sqlContent == null || sqlContent.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "SQL不能为空"));

        SqlSnippet s = new SqlSnippet();
        s.setUserId(uid);
        s.setTitle(title.trim());
        s.setSqlContent(sqlContent.trim());
        s.setDescription(body.getOrDefault("description", ""));
        s.setTags(body.getOrDefault("tags", ""));
        snippetRepo.insert(s);
        return ResponseEntity.ok(Map.of("success", true, "message", "片段已保存"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        String uid = userId(auth);
        SqlSnippet s = snippetRepo.findById(id, uid);
        if (s == null) return ResponseEntity.badRequest().body(Map.of("error", "片段不存在"));

        if (body.containsKey("title")) s.setTitle(body.get("title").trim());
        if (body.containsKey("sqlContent")) s.setSqlContent(body.get("sqlContent").trim());
        if (body.containsKey("description")) s.setDescription(body.get("description"));
        if (body.containsKey("tags")) s.setTags(body.get("tags"));
        snippetRepo.update(s);
        return ResponseEntity.ok(Map.of("success", true, "message", "片段已更新"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        String uid = userId(auth);
        SqlSnippet s = snippetRepo.findById(id, uid);
        if (s == null) return ResponseEntity.badRequest().body(Map.of("error", "片段不存在"));
        snippetRepo.delete(id, uid);
        return ResponseEntity.ok(Map.of("success", true, "message", "片段已删除"));
    }
}
