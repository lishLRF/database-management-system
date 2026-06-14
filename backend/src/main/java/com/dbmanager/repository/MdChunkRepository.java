package com.dbmanager.repository;

import com.dbmanager.entity.MdChunk;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class MdChunkRepository {

    private final JdbcTemplate jdbc;

    public MdChunkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<MdChunk> rowMapper = (rs, rowNum) -> {
        MdChunk c = new MdChunk();
        c.setId(rs.getLong("id"));
        c.setDocumentId(rs.getLong("document_id"));
        c.setChunkIndex(rs.getInt("chunk_index"));
        c.setChunkType(rs.getString("chunk_type"));
        c.setChunkTitle(rs.getString("chunk_title"));
        c.setContentStartPos(rs.getInt("content_start_pos"));
        c.setContentEndPos(rs.getInt("content_end_pos"));
        c.setChunkContent(rs.getString("chunk_content"));
        c.setClassificationReason(rs.getString("classification_reason"));
        c.setHumanModified(rs.getBoolean("human_modified"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) c.setCreatedAt(ca.toLocalDateTime());
        return c;
    };

    public void save(MdChunk chunk) {
        jdbc.update(
            "INSERT INTO md_chunks (document_id, chunk_index, chunk_type, chunk_title, content_start_pos, content_end_pos, chunk_content, classification_reason, human_modified) VALUES (?,?,?,?,?,?,?,?,?)",
            chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getChunkType(), chunk.getChunkTitle(),
            chunk.getContentStartPos(), chunk.getContentEndPos(), chunk.getChunkContent(),
            chunk.getClassificationReason(), chunk.getHumanModified() != null ? chunk.getHumanModified() : false
        );
    }

    public void batchInsert(List<MdChunk> chunks) {
        jdbc.batchUpdate(
            "INSERT INTO md_chunks (document_id, chunk_index, chunk_type, chunk_title, content_start_pos, content_end_pos, chunk_content, classification_reason, human_modified) VALUES (?,?,?,?,?,?,?,?,?)",
            new BatchPreparedStatementSetter() {
                @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                    MdChunk c = chunks.get(i);
                    ps.setLong(1, c.getDocumentId());
                    ps.setInt(2, c.getChunkIndex());
                    ps.setString(3, c.getChunkType());
                    ps.setString(4, c.getChunkTitle());
                    ps.setInt(5, c.getContentStartPos());
                    ps.setInt(6, c.getContentEndPos());
                    ps.setString(7, c.getChunkContent());
                    ps.setString(8, c.getClassificationReason());
                    ps.setBoolean(9, c.getHumanModified() != null ? c.getHumanModified() : false);
                }
                @Override public int getBatchSize() { return chunks.size(); }
            }
        );
    }

    public List<MdChunk> findByDocumentId(Long docId) {
        return jdbc.query("SELECT * FROM md_chunks WHERE document_id = ? ORDER BY chunk_index ASC", rowMapper, docId);
    }

    public void deleteByDocumentId(Long docId) {
        jdbc.update("DELETE FROM md_chunks WHERE document_id = ?", docId);
    }

    public void updateChunk(Long id, String chunkType, String chunkTitle, boolean humanModified) {
        jdbc.update("UPDATE md_chunks SET chunk_type = ?, chunk_title = ?, human_modified = ? WHERE id = ?",
            chunkType, chunkTitle, humanModified, id);
    }
}
