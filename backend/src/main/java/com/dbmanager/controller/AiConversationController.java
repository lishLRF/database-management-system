package com.dbmanager.controller;

import com.dbmanager.entity.AiConversation;
import com.dbmanager.repository.AiConversationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai/conversations")
public class AiConversationController {

    private final AiConversationRepository convRepo;

    public AiConversationController(AiConversationRepository convRepo) {
        this.convRepo = convRepo;
    }

    @GetMapping
    public ResponseEntity<?> listConversations(Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        List<String> convIds = convRepo.findConversationIdsByUserId(userId, 50);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String cid : convIds) {
            List<AiConversation> msgs = convRepo.findByConversationId(cid);
            String title = cid.substring(0, 8);
            if (!msgs.isEmpty()) {
                String first = msgs.get(0).getMessageContent();
                title = first.length() > 40 ? first.substring(0, 40) + "..." : first;
            }
            Map<String, Object> info = new HashMap<>();
            info.put("conversationId", cid);
            info.put("title", title);
            info.put("messageCount", msgs.size());
            info.put("createdAt", msgs.isEmpty() ? null : msgs.get(0).getCreatedAt());
            result.add(info);
        }
        return ResponseEntity.ok(Map.of("success", true, "conversations", result));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getMessages(@PathVariable String conversationId) {
        List<AiConversation> messages = convRepo.findByConversationId(conversationId);
        return ResponseEntity.ok(Map.of("success", true, "messages", messages));
    }

    @PostMapping("/{conversationId}/save")
    public ResponseEntity<?> saveMessage(@PathVariable String conversationId,
                                          @RequestBody Map<String, Object> request,
                                          Authentication auth) {
        try {
            String userId = auth != null ? auth.getName() : "anonymous";
            Long connectionId = Long.valueOf(request.get("connectionId").toString());
            String messageType = (String) request.get("messageType");
            String content = (String) request.get("content");

            AiConversation msg = new AiConversation();
            msg.setConversationId(conversationId);
            msg.setUserId(userId);
            msg.setConnectionId(connectionId);
            msg.setMessageType(messageType);
            msg.setMessageContent(content);
            convRepo.save(msg);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> deleteConversation(@PathVariable String conversationId) {
        convRepo.deleteByConversationId(conversationId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
