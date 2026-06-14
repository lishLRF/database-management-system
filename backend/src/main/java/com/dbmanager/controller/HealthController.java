package com.dbmanager.controller;

import com.dbmanager.service.ConnectionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ConnectionService connectionService;
    private final DataSource dataSource;

    public HealthController(ConnectionService connectionService, DataSource dataSource) {
        this.connectionService = connectionService;
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Database Management System is running");
        response.put("version", "1.0.0");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            response.put("userId", auth.getPrincipal());
        }

        return response;
    }

    @GetMapping("/health/pool")
    public Map<String, Object> poolStatus() {
        var stats = connectionService.getPoolStats(dataSource);
        Map<String, Object> resp = new HashMap<>();
        resp.put("activeConnections", stats.getActiveConnections());
        resp.put("idleConnections", stats.getIdleConnections());
        resp.put("totalConnections", stats.getTotalConnections());
        resp.put("waitingThreads", stats.getWaitingThreads());
        resp.put("perPool", stats.getPerPool());
        resp.put("connectionCacheSize", stats.getPerPool().size());
        return resp;
    }

    @PostMapping("/health/evict-idle")
    public Map<String, Object> evictIdlePools() {
        int removed = connectionService.evictIdlePools();
        return Map.of("removed", removed, "message", "已释放 " + removed + " 个空闲连接池");
    }
}
