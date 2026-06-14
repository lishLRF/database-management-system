package com.dbmanager.repository;

import com.dbmanager.entity.MdDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class MdDocumentRepository {

    private final JdbcTemplate jdbc;

    public MdDocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<MdDocument> rowMapper = (rs, rowNum) -> {
        MdDocument d = new MdDocument();
        d.setId(rs.getLong("id"));
        d.setUserId(rs.getString("user_id"));
        d.setFileName(rs.getString("file_name"));
        d.setFileSize(rs.getLong("file_size"));
        d.setFullContent(rs.getString("full_content"));
        d.setAiSummary(rs.getString("ai_summary"));
        d.setStatus(rs.getString("status"));
        Timestamp ca = rs.getTimestamp("created_at");
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ca != null) d.setCreatedAt(ca.toLocalDateTime());
        if (ua != null) d.setUpdatedAt(ua.toLocalDateTime());
        return d;
    };

    public Long save(MdDocument doc) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO md_documents (user_id, file_name, file_size, full_content, ai_summary, status) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, doc.getUserId());
            ps.setString(2, doc.getFileName());
            ps.setLong(3, doc.getFileSize());
            ps.setString(4, doc.getFullContent());
            ps.setString(5, doc.getAiSummary());
            ps.setString(6, doc.getStatus() != null ? doc.getStatus() : "uploaded");
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key != null ? key.longValue() : null;
    }

    public MdDocument findById(Long id) {
        List<MdDocument> list = jdbc.query("SELECT * FROM md_documents WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<MdDocument> findByUserId(String userId) {
        return jdbc.query("SELECT * FROM md_documents WHERE user_id = ? ORDER BY created_at DESC", rowMapper, userId);
    }

    public void updateSummary(Long id, String summary, String status) {
        jdbc.update("UPDATE md_documents SET ai_summary = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            summary, status, id);
    }

    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE md_documents SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM md_documents WHERE id = ?", id);
    }
}
