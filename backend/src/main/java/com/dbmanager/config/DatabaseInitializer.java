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
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                sqlBuilder.append(line).append(" ");

                if (line.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    jdbcTemplate.execute(sql);
                    sqlBuilder.setLength(0);
                }
            }

            log.info("数据库表结构初始化完成");
        }
    }
}
