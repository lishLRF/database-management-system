package com.dbmanager.repository;

import com.dbmanager.entity.AiConversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AiConversation> rowMapper = (rs, rowNum) -> {
        AiConversation c = new AiConversation();
        c.setId(rs.getLong("id"));
        c.setConversationId(rs.getString("conversation_id"));
        c.setUserId(rs.getString("user_id"));
        c.setConnectionId(rs.getObject("connection_id", Long.class));
        c.setMessageType(rs.getString("message_type"));
        c.setMessageContent(rs.getString("message_content"));
        c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return c;
    };

    public void save(AiConversation conversation) {
        String sql = """
            INSERT INTO ai_conversations (conversation_id, user_id, connection_id, message_type, message_content)
            VALUES (?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, conversation.getConversationId(), conversation.getUserId(),
            conversation.getConnectionId(), conversation.getMessageType(),
            conversation.getMessageContent());
    }

    public List<AiConversation> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM ai_conversations WHERE conversation_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    public List<String> findConversationIdsByUserId(String userId, int limit) {
        String sql = """
            SELECT DISTINCT conversation_id FROM ai_conversations
            WHERE user_id = ? ORDER BY conversation_id DESC LIMIT ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("conversation_id"), userId, limit);
    }

    public void deleteByConversationId(String conversationId) {
        String sql = "DELETE FROM ai_conversations WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }
}
