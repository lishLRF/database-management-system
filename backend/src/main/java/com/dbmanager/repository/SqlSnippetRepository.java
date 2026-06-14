package com.dbmanager.repository;

import com.dbmanager.entity.SqlSnippet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SqlSnippetRepository {

    private final JdbcTemplate jdbcTemplate;

    public SqlSnippetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SqlSnippet> rowMapper = new RowMapper<SqlSnippet>() {
        @Override
        public SqlSnippet mapRow(ResultSet rs, int rowNum) throws SQLException {
            SqlSnippet s = new SqlSnippet();
            s.setId(rs.getLong("id"));
            s.setUserId(rs.getString("user_id"));
            s.setTitle(rs.getString("title"));
            s.setSqlContent(rs.getString("sql_content"));
            s.setDescription(rs.getString("description"));
            s.setTags(rs.getString("tags"));
            s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            s.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return s;
        }
    };

    public List<SqlSnippet> findByUserId(String userId) {
        return jdbcTemplate.query(
            "SELECT * FROM sql_snippets WHERE user_id = ? ORDER BY updated_at DESC",
            rowMapper, userId);
    }

    public List<SqlSnippet> searchByUserId(String userId, String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            return jdbcTemplate.query(
                "SELECT * FROM sql_snippets WHERE user_id = ? AND (title LIKE ? OR description LIKE ? OR tags LIKE ? OR sql_content LIKE ?) ORDER BY updated_at DESC",
                rowMapper, userId, kw, kw, kw, kw);
        }
        return findByUserId(userId);
    }

    public SqlSnippet findById(Long id, String userId) {
        List<SqlSnippet> list = jdbcTemplate.query(
            "SELECT * FROM sql_snippets WHERE id = ? AND user_id = ?",
            rowMapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insert(SqlSnippet s) {
        jdbcTemplate.update(
            "INSERT INTO sql_snippets (user_id, title, sql_content, description, tags) VALUES (?, ?, ?, ?, ?)",
            s.getUserId(), s.getTitle(), s.getSqlContent(), s.getDescription(), s.getTags());
    }

    public void update(SqlSnippet s) {
        jdbcTemplate.update(
            "UPDATE sql_snippets SET title=?, sql_content=?, description=?, tags=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
            s.getTitle(), s.getSqlContent(), s.getDescription(), s.getTags(), s.getId(), s.getUserId());
    }

    public void delete(Long id, String userId) {
        jdbcTemplate.update("DELETE FROM sql_snippets WHERE id = ? AND user_id = ?", id, userId);
    }
}
