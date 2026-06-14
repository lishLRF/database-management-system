package com.dbmanager.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 数据库初始化器
 * 在应用启动时自动检查并创建内置SQLite元数据库的表结构
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        log.info("开始初始化内置SQLite元数据库...");

        try {
            ensureDataDirectoryExists();
            executeSchemaScript();
            log.info("元数据库初始化完成");
        } catch (Exception e) {
            log.error("元数据库初始化失败", e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    private void ensureDataDirectoryExists() throws Exception {
        Path dataDir = Paths.get("./data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
            log.info("创建数据目录: {}", dataDir.toAbsolutePath());
        }
    }

    private void executeSchemaScript() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/schema.sql");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Keep only full-line comments for skipping
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                // Strip inline comments to protect SQL from line-join corruption
                int commentIdx = trimmed.indexOf("--");
                if (commentIdx >= 0) {
                    trimmed = trimmed.substring(0, commentIdx).trim();
                    if (trimmed.isEmpty()) continue;
                }
                sqlBuilder.append(trimmed).append(" ");

                if (trimmed.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    if (sql.startsWith("ALTER TABLE")) {
                        handleAlterTable(sql);
                    } else {
                        try {
                            jdbcTemplate.execute(sql);
                        } catch (Exception e) {
                            String errMsg = e.getMessage();
                            // "already exists" is harmless for CREATE IF NOT EXISTS
                            if (errMsg != null && errMsg.contains("already exists")) {
                                log.debug("已存在，跳过: {}", sql.substring(0, Math.min(60, sql.length())) + "...");
                            } else {
                                log.error("SQL执行失败: {}\nSQL: {}\n错误: {}", e.getClass().getSimpleName(),
                                    sql.substring(0, Math.min(200, sql.length())), errMsg);
                                throw e;
                            }
                        }
                    }
                    sqlBuilder.setLength(0);
                }
            }

            log.info("数据库表结构初始化完成");
        }
    }

    private void handleAlterTable(String sql) {
        // Extract TABLE and COLUMN from "ALTER TABLE table ADD COLUMN col TYPE DEFAULT ..."
        String[] parts = sql.split("\\s+");
        if (parts.length < 6) {
            log.warn("无法解析 ALTER TABLE: {}", sql);
            return;
        }
        String table = parts[2]; // ALTER TABLE <table> ADD ...
        String colName = parts[5]; // ALTER TABLE t ADD COLUMN <name> TYPE ...
        // Check if column already exists
        try {
            List<Map<String, Object>> cols = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
            for (Map<String, Object> col : cols) {
                if (colName.equals(col.get("name"))) {
                    log.debug("列已存在 {}.{}，跳过 ALTER TABLE", table, colName);
                    return;
                }
            }
        } catch (Exception e) {
            // Table doesn't exist yet, skip ALTER
            log.debug("表 {} 不存在，跳过 ALTER TABLE", table);
            return;
        }
        try {
            jdbcTemplate.execute(sql);
            log.info("成功添加列: {}.{}", table, colName);
        } catch (Exception e) {
            log.warn("ALTER TABLE 失败（可能已存在）: {}", e.getMessage());
        }
    }
}
