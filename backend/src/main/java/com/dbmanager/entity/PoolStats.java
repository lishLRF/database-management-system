package com.dbmanager.entity;

import java.util.Map;

public class PoolStats {
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int waitingThreads;
    private Map<String, Object> perPool;

    public int getActiveConnections() { return activeConnections; }
    public void setActiveConnections(int v) { this.activeConnections = v; }
    public int getIdleConnections() { return idleConnections; }
    public void setIdleConnections(int v) { this.idleConnections = v; }
    public int getTotalConnections() { return totalConnections; }
    public void setTotalConnections(int v) { this.totalConnections = v; }
    public int getWaitingThreads() { return waitingThreads; }
    public void setWaitingThreads(int v) { this.waitingThreads = v; }
    public Map<String, Object> getPerPool() { return perPool; }
    public void setPerPool(Map<String, Object> v) { this.perPool = v; }
}
