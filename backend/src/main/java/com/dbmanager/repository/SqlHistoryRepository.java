package com.dbmanager.repository;

import com.dbmanager.entity.SqlHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SqlHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public SqlHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SqlHistory> rowMapper = new RowMapper<SqlHistory>() {
        @Override
        public SqlHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            SqlHistory history = new SqlHistory();
            history.setId(rs.getLong("id"));
            history.setUserId(rs.getString("user_id"));
            history.setConnectionId(rs.getLong("connection_id"));
            history.setSqlText(rs.getString("sql_text"));
            history.setSqlType(rs.getString("sql_type"));
            history.setTargetTable(rs.getString("target_table"));
            history.setSource(rs.getString("source"));
            history.setExecutionTime(rs.getInt("execution_time"));
            history.setRowsAffected(rs.getInt("rows_affected"));
            history.setStatus(rs.getString("status"));
            history.setErrorMessage(rs.getString("error_message"));
            history.setIsVisible(rs.getBoolean("is_visible"));
            history.setExecutedAt(rs.getTimestamp("executed_at").toLocalDateTime());
            return history;
        }
    };

    /**
     * 插入历史记录
     */
    public void insert(SqlHistory history) {
        String sql = "INSERT INTO sql_history (user_id, connection_id, sql_text, sql_type, target_table, source, " +
                     "execution_time, rows_affected, status, error_message, is_visible) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
            history.getUserId(),
            history.getConnectionId(),
            history.getSqlText(),
            history.getSqlType(),
            history.getTargetTable(),
            history.getSource(),
            history.getExecutionTime(),
            history.getRowsAffected(),
            history.getStatus(),
            history.getErrorMessage(),
            history.getIsVisible() != null ? history.getIsVisible() : true
        );
    }

    /**
     * 查询可见的历史记录（按时间倒序，最近50条）
     */
    public List<SqlHistory> findVisibleByUserId(String userId, int limit) {
        String sql = "SELECT * FROM sql_history WHERE user_id = ? AND is_visible = 1 " +
                     "ORDER BY executed_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, userId, limit);
    }

    /**
     * 搜索历史记录（支持文本搜索 + 分页 + 过滤）
     */
    public List<SqlHistory> search(String userId, String tableName, String sqlType, String search, int offset, int pageSize) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM sql_history WHERE user_id = ? AND is_visible = 1");
        List<Object> params = new java.util.ArrayList<>();
        params.add(userId);

        if (tableName != null && !tableName.trim().isEmpty()) {
            sql.append(" AND target_table = ?");
            params.add(tableName.trim());
        }
        if (sqlType != null && !sqlType.trim().isEmpty()) {
            sql.append(" AND sql_type = ?");
            params.add(sqlType.trim());
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (sql_text LIKE ? OR target_table LIKE ?)");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
        }
        sql.append(" ORDER BY executed_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * 计数（与 search 同样的过滤条件）
     */
    public int count(String userId, String tableName, String sqlType, String search) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM sql_history WHERE user_id = ? AND is_visible = 1");
        List<Object> params = new java.util.ArrayList<>();
        params.add(userId);

        if (tableName != null && !tableName.trim().isEmpty()) {
            sql.append(" AND target_table = ?");
            params.add(tableName.trim());
        }
        if (sqlType != null && !sqlType.trim().isEmpty()) {
            sql.append(" AND sql_type = ?");
            params.add(sqlType.trim());
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (sql_text LIKE ? OR target_table LIKE ?)");
            String kw = "%" + search.trim() + "%";
            params.add(kw);
            params.add(kw);
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    /**
     * 软删除（标记为不可见）
     */
    public void softDelete(Long id, String userId) {
        String sql = "UPDATE sql_history SET is_visible = 0 WHERE id = ? AND user_id = ?";
        jdbcTemplate.update(sql, id, userId);
    }

    /**
     * 清空历史（批量软删除）
     */
    public void clearAll(String userId) {
        String sql = "UPDATE sql_history SET is_visible = 0 WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}
