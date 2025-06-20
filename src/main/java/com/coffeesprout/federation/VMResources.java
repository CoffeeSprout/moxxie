package com.coffeesprout.federation;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Represents resource usage and allocation for a VM
 */
public class VMResources {
    
    private String vmId;
    private String vmName;
    private String nodeId;
    private String nodeName;
    private String status;  // running, stopped, suspended
    
    // Allocated resources
    private int allocatedCpuCores;
    private long allocatedMemoryBytes;
    private long allocatedStorageBytes;
    
    // Actual usage
    private double cpuUsagePercent;
    private double cpuUsageGHz;
    private long memoryUsedBytes;
    private long storageUsedBytes;
    
    // Network usage
    private long networkInBps;
    private long networkOutBps;
    
    // Storage I/O
    private long diskReadBps;
    private long diskWriteBps;
    private long diskIops;
    
    // Tags and metadata
    private Set<String> tags;
    private Map<String, String> labels;
    private Map<String, Object> metadata;
    
    // Timestamps
    private Instant createdAt;
    private Instant lastUpdated;
    private long uptimeSeconds;
    
    // Cost tracking
    private double hourlyRate;
    private double monthlyRate;
    
    // Getters and setters
    public String getVmId() { return vmId; }
    public void setVmId(String vmId) { this.vmId = vmId; }
    
    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }
    
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public int getAllocatedCpuCores() { return allocatedCpuCores; }
    public void setAllocatedCpuCores(int allocatedCpuCores) { this.allocatedCpuCores = allocatedCpuCores; }
    
    public long getAllocatedMemoryBytes() { return allocatedMemoryBytes; }
    public void setAllocatedMemoryBytes(long allocatedMemoryBytes) { this.allocatedMemoryBytes = allocatedMemoryBytes; }
    
    public long getAllocatedStorageBytes() { return allocatedStorageBytes; }
    public void setAllocatedStorageBytes(long allocatedStorageBytes) { this.allocatedStorageBytes = allocatedStorageBytes; }
    
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }
    
    public double getCpuUsageGHz() { return cpuUsageGHz; }
    public void setCpuUsageGHz(double cpuUsageGHz) { this.cpuUsageGHz = cpuUsageGHz; }
    
    public long getMemoryUsedBytes() { return memoryUsedBytes; }
    public void setMemoryUsedBytes(long memoryUsedBytes) { this.memoryUsedBytes = memoryUsedBytes; }
    
    public long getStorageUsedBytes() { return storageUsedBytes; }
    public void setStorageUsedBytes(long storageUsedBytes) { this.storageUsedBytes = storageUsedBytes; }
    
    public long getNetworkInBps() { return networkInBps; }
    public void setNetworkInBps(long networkInBps) { this.networkInBps = networkInBps; }
    
    public long getNetworkOutBps() { return networkOutBps; }
    public void setNetworkOutBps(long networkOutBps) { this.networkOutBps = networkOutBps; }
    
    public long getDiskReadBps() { return diskReadBps; }
    public void setDiskReadBps(long diskReadBps) { this.diskReadBps = diskReadBps; }
    
    public long getDiskWriteBps() { return diskWriteBps; }
    public void setDiskWriteBps(long diskWriteBps) { this.diskWriteBps = diskWriteBps; }
    
    public long getDiskIops() { return diskIops; }
    public void setDiskIops(long diskIops) { this.diskIops = diskIops; }
    
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
    
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
    
    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
    
    public double getMonthlyRate() { return monthlyRate; }
    public void setMonthlyRate(double monthlyRate) { this.monthlyRate = monthlyRate; }
}