package com.coffeesprout.federation;

import java.util.Map;
import java.util.Set;

/**
 * Represents a storage pool in the cluster
 */
public class StoragePool {

    private String poolId;
    private String poolName;
    private String type;  // local, nfs, ceph, iscsi, etc.
    private String storageClass;  // SSD, HDD, NVMe

    // Capacity information
    private long totalBytes;
    private long usedBytes;
    private long availableBytes;
    private long allocatedBytes;  // Sum of all VM disk allocations

    // Performance characteristics
    private Long maxIops;
    private Long maxBandwidthBps;
    private Double averageLatencyMs;

    // Availability
    private boolean active;
    private boolean shared;  // Shared across nodes
    private Set<String> accessibleNodes;  // Nodes that can access this pool

    // Features
    private boolean supportsThinProvisioning;
    private boolean supportsSnapshots;
    private boolean supportsReplication;
    private boolean supportsEncryption;

    // Usage limits
    private Double maxOverprovisionRatio;
    private Long maxVolumeSize;
    private Integer maxVolumes;

    // Metadata
    private Map<String, String> labels;
    private Map<String, Object> metadata;

    // Getters and setters
    public String getPoolId() { return poolId; }
    public void setPoolId(String poolId) { this.poolId = poolId; }

    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStorageClass() { return storageClass; }
    public void setStorageClass(String storageClass) { this.storageClass = storageClass; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

    public long getAvailableBytes() { return availableBytes; }
    public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }

    public long getAllocatedBytes() { return allocatedBytes; }
    public void setAllocatedBytes(long allocatedBytes) { this.allocatedBytes = allocatedBytes; }

    public Long getMaxIops() { return maxIops; }
    public void setMaxIops(Long maxIops) { this.maxIops = maxIops; }

    public Long getMaxBandwidthBps() { return maxBandwidthBps; }
    public void setMaxBandwidthBps(Long maxBandwidthBps) { this.maxBandwidthBps = maxBandwidthBps; }

    public Double getAverageLatencyMs() { return averageLatencyMs; }
    public void setAverageLatencyMs(Double averageLatencyMs) { this.averageLatencyMs = averageLatencyMs; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }

    public Set<String> getAccessibleNodes() { return accessibleNodes; }
    public void setAccessibleNodes(Set<String> accessibleNodes) { this.accessibleNodes = accessibleNodes; }

    public boolean isSupportsThinProvisioning() { return supportsThinProvisioning; }
    public void setSupportsThinProvisioning(boolean supportsThinProvisioning) { this.supportsThinProvisioning = supportsThinProvisioning; }

    public boolean isSupportsSnapshots() { return supportsSnapshots; }
    public void setSupportsSnapshots(boolean supportsSnapshots) { this.supportsSnapshots = supportsSnapshots; }

    public boolean isSupportsReplication() { return supportsReplication; }
    public void setSupportsReplication(boolean supportsReplication) { this.supportsReplication = supportsReplication; }

    public boolean isSupportsEncryption() { return supportsEncryption; }
    public void setSupportsEncryption(boolean supportsEncryption) { this.supportsEncryption = supportsEncryption; }

    public Double getMaxOverprovisionRatio() { return maxOverprovisionRatio; }
    public void setMaxOverprovisionRatio(Double maxOverprovisionRatio) { this.maxOverprovisionRatio = maxOverprovisionRatio; }

    public Long getMaxVolumeSize() { return maxVolumeSize; }
    public void setMaxVolumeSize(Long maxVolumeSize) { this.maxVolumeSize = maxVolumeSize; }

    public Integer getMaxVolumes() { return maxVolumes; }
    public void setMaxVolumes(Integer maxVolumes) { this.maxVolumes = maxVolumes; }

    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
