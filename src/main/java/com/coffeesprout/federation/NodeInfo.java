package com.coffeesprout.federation;

import java.util.Map;

/**
 * Basic information about a node in the cluster
 */
public class NodeInfo {
    
    private String nodeId;
    private String nodeName;
    private String status;  // online, offline, maintenance
    private String ipAddress;
    
    // Basic capacity info
    private int cpuCores;
    private long memoryBytes;
    private long storageBytes;
    
    // Current utilization
    private double cpuUsagePercent;
    private double memoryUsagePercent;
    private double storageUsagePercent;
    
    // VM count
    private int totalVMs;
    private int runningVMs;
    
    // Labels and metadata
    private Map<String, String> labels;
    private Map<String, Object> metadata;
    
    // Getters and setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
    
    public long getMemoryBytes() { return memoryBytes; }
    public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }
    
    public long getStorageBytes() { return storageBytes; }
    public void setStorageBytes(long storageBytes) { this.storageBytes = storageBytes; }
    
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }
    
    public double getMemoryUsagePercent() { return memoryUsagePercent; }
    public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }
    
    public double getStorageUsagePercent() { return storageUsagePercent; }
    public void setStorageUsagePercent(double storageUsagePercent) { this.storageUsagePercent = storageUsagePercent; }
    
    public int getTotalVMs() { return totalVMs; }
    public void setTotalVMs(int totalVMs) { this.totalVMs = totalVMs; }
    
    public int getRunningVMs() { return runningVMs; }
    public void setRunningVMs(int runningVMs) { this.runningVMs = runningVMs; }
    
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}