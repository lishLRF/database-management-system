package com.dbmanager.service;

import com.dbmanager.entity.DbConnection;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.util.AESUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConnectionService {

    private final ConnectionRepository repository;
    private final AESUtil aesUtil;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public ConnectionService(ConnectionRepository repository, AESUtil aesUtil) {
        this.repository = repository;
        this.aesUtil = aesUtil;
    }

    public DbConnection createConnection(DbConnection connection, String plainPassword) throws Exception {
        String encrypted = aesUtil.encrypt(plainPassword);
        connection.setPasswordEncrypted(encrypted);
        return repository.save(connection);
    }

    public List<DbConnection> getAllConnections(String userId) {
        return repository.findByUserId(userId);
    }

    public boolean testConnection(Long connectionId) {
        DbConnection conn = repository.findById(connectionId);
        if (conn == null) return false;

        try {
            String password = aesUtil.decrypt(conn.getPasswordEncrypted());
            HikariDataSource ds = buildDataSource(conn, password);
            try (Connection c = ds.getConnection()) {
                return c.isValid(5);
            } finally {
                ds.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public DbConnection getConnection(Long connectionId) {
        return repository.findById(connectionId);
    }

    public void deleteConnection(Long connectionId) {
        HikariDataSource ds = dataSourceCache.remove(connectionId);
        if (ds != null) ds.close();
        repository.deleteById(connectionId);
    }

    public HikariDataSource getOrCreateDataSource(Long connectionId) throws Exception {
        return dataSourceCache.computeIfAbsent(connectionId, id -> {
            DbConnection conn = repository.findById(id);
            if (conn == null) throw new RuntimeException("Connection not found");
            try {
                String password = aesUtil.decrypt(conn.getPasswordEncrypted());
                return buildDataSource(conn, password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private HikariDataSource buildDataSource(DbConnection conn, String password) {
        HikariConfig config = new HikariConfig();
        if ("postgresql".equals(conn.getDbType())) {
            config.setJdbcUrl("jdbc:postgresql://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabaseName());
            config.setUsername(conn.getUsername());
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
        } else if ("sqlite".equals(conn.getDbType())) {
            config.setJdbcUrl("jdbc:sqlite:" + conn.getDatabaseName());
            config.setDriverClassName("org.sqlite.JDBC");
        }
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }
}
