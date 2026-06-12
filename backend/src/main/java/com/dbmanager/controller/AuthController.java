package com.dbmanager.controller;

import com.dbmanager.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    @Value("${app.auth.debug-mode}")
    private boolean debugMode;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/debug-login")
    public ResponseEntity<?> debugLogin(@RequestBody Map<String, String> request) {
        if (!debugMode) {
            return ResponseEntity.status(403).body(Map.of("message", "Debug mode is disabled"));
        }

        String userId = request.get("userId");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "userId is required"));
        }

        String token = jwtUtil.generateToken(userId);
        return ResponseEntity.ok(Map.of("token", token, "userId", userId));
    }
}
