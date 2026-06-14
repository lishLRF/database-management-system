package com.dbmanager.service;

import com.dbmanager.entity.DbConnection;
import com.dbmanager.entity.PoolStats;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.util.AESUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
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
        config.setMaximumPoolSize(10);   // supports 20 users × 2-3 active queries each
        config.setMinimumIdle(2);        // keep 2 warm connections
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(1800000);  // 30 min idle → release
        config.setMaxLifetime(3600000);  // 1 hour max lifetime
        return new HikariDataSource(config);
    }

    /** Collect pool stats from all active data sources */
    public PoolStats getPoolStats(DataSource metaDataSource) {
        PoolStats stats = new PoolStats();
        int active = 0, idle = 0, total = 0, waiting = 0;
        Map<String, Object> perPool = new HashMap<>();

        // Metadata DB pool
        if (metaDataSource instanceof HikariDataSource hds) {
            var mx = hds.getHikariPoolMXBean();
            if (mx != null) {
                active += mx.getActiveConnections();
                idle += mx.getIdleConnections();
                total += mx.getTotalConnections();
                waiting += mx.getThreadsAwaitingConnection();
                perPool.put("metadata", Map.of(
                    "active", mx.getActiveConnections(),
                    "idle", mx.getIdleConnections(),
                    "total", mx.getTotalConnections(),
                    "waiting", mx.getThreadsAwaitingConnection()
                ));
            }
        }

        // User connection pools
        for (Map.Entry<Long, HikariDataSource> e : dataSourceCache.entrySet()) {
            var mx = e.getValue().getHikariPoolMXBean();
            if (mx != null) {
                active += mx.getActiveConnections();
                idle += mx.getIdleConnections();
                total += mx.getTotalConnections();
                waiting += mx.getThreadsAwaitingConnection();
                perPool.put("conn-" + e.getKey(), Map.of(
                    "active", mx.getActiveConnections(),
                    "idle", mx.getIdleConnections(),
                    "total", mx.getTotalConnections(),
                    "waiting", mx.getThreadsAwaitingConnection()
                ));
            }
        }

        stats.setActiveConnections(active);
        stats.setIdleConnections(idle);
        stats.setTotalConnections(total);
        stats.setWaitingThreads(waiting);
        stats.setPerPool(perPool);
        return stats;
    }

    /** Release idle connection pools that haven't been used */
    public int evictIdlePools() {
        int removed = 0;
        for (Map.Entry<Long, HikariDataSource> e : dataSourceCache.entrySet()) {
            var mx = e.getValue().getHikariPoolMXBean();
            if (mx != null && mx.getActiveConnections() == 0 && mx.getIdleConnections() == mx.getTotalConnections()) {
                // All connections idle — safe to remove
                e.getValue().close();
                dataSourceCache.remove(e.getKey());
                removed++;
            }
        }
        return removed;
    }
}
