package com.dbmanager.repository;

import com.dbmanager.entity.AiConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AiConfig> rowMapper = (rs, rowNum) -> {
        AiConfig config = new AiConfig();
        config.setId(rs.getLong("id"));
        config.setUserId(rs.getString("user_id"));
        config.setApiProvider(rs.getString("api_provider"));
        config.setApiKeyEncrypted(rs.getString("api_key_encrypted"));
        config.setApiBaseUrl(rs.getString("api_base_url"));
        config.setModelName(rs.getString("model_name"));
        config.setTemperature(rs.getDouble("temperature"));
        config.setMaxTokens(rs.getInt("max_tokens"));
        config.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        config.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return config;
    };

    public AiConfig findByUserId(String userId) {
        String sql = "SELECT * FROM ai_configs WHERE user_id = ?";
        List<AiConfig> results = jdbcTemplate.query(sql, rowMapper, userId);
        return results.isEmpty() ? null : results.get(0);
    }

    public AiConfig save(AiConfig config) {
        AiConfig existing = findByUserId(config.getUserId());
        if (existing != null) {
            String sql = """
                UPDATE ai_configs SET api_provider=?, api_key_encrypted=?, api_base_url=?,
                model_name=?, temperature=?, max_tokens=?, updated_at=CURRENT_TIMESTAMP
                WHERE user_id=?
            """;
            jdbcTemplate.update(sql, config.getApiProvider(), config.getApiKeyEncrypted(),
                config.getApiBaseUrl(), config.getModelName(), config.getTemperature(),
                config.getMaxTokens(), config.getUserId());
            return findByUserId(config.getUserId());
        } else {
            String sql = """
                INSERT INTO ai_configs (user_id, api_provider, api_key_encrypted, api_base_url, model_name, temperature, max_tokens)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            jdbcTemplate.update(sql, config.getUserId(), config.getApiProvider(),
                config.getApiKeyEncrypted(), config.getApiBaseUrl(), config.getModelName(),
                config.getTemperature(), config.getMaxTokens());
            return findByUserId(config.getUserId());
        }
    }
}
