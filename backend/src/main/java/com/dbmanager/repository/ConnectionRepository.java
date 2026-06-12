package com.dbmanager.repository;

import com.dbmanager.entity.DbConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConnectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initTable();
    }

    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS db_connections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                db_type TEXT NOT NULL,
                host TEXT,
                port INTEGER,
                database_name TEXT NOT NULL,
                username TEXT,
                password_encrypted TEXT,
                is_active INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """;
        jdbcTemplate.execute(sql);
    }

    private final RowMapper<DbConnection> rowMapper = (rs, rowNum) -> {
        DbConnection conn = new DbConnection();
        conn.setId(rs.getLong("id"));
        conn.setUserId(rs.getString("user_id"));
        conn.setName(rs.getString("name"));
        conn.setDbType(rs.getString("db_type"));
        conn.setHost(rs.getString("host"));
        conn.setPort(rs.getObject("port", Integer.class));
        conn.setDatabaseName(rs.getString("database_name"));
        conn.setUsername(rs.getString("username"));
        conn.setPasswordEncrypted(rs.getString("password_encrypted"));
        conn.setIsActive(rs.getInt("is_active") == 1);
        conn.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        conn.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return conn;
    };

    public DbConnection save(DbConnection connection) {
        String sql = """
            INSERT INTO db_connections (user_id, name, db_type, host, port, database_name, username, password_encrypted, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, connection.getUserId(), connection.getName(), connection.getDbType(),
                connection.getHost(), connection.getPort(), connection.getDatabaseName(),
                connection.getUsername(), connection.getPasswordEncrypted(), connection.getIsActive() ? 1 : 0);

        // 查询刚插入的记录获取生成的ID
        String querySql = "SELECT * FROM db_connections WHERE user_id = ? AND name = ? ORDER BY id DESC LIMIT 1";
        return jdbcTemplate.queryForObject(querySql, rowMapper, connection.getUserId(), connection.getName());
    }

    public List<DbConnection> findByUserId(String userId) {
        String sql = "SELECT * FROM db_connections WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public DbConnection findById(Long id) {
        String sql = "SELECT * FROM db_connections WHERE id = ?";
        List<DbConnection> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM db_connections WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void updateActive(String userId, Long activeId) {
        jdbcTemplate.update("UPDATE db_connections SET is_active = 0 WHERE user_id = ?", userId);
        if (activeId != null) {
            jdbcTemplate.update("UPDATE db_connections SET is_active = 1 WHERE id = ?", activeId);
        }
    }
}
