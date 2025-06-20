package com.coffeesprout.api.dto.federation;

import java.util.Map;
import java.util.Set;

/**
 * Response DTO for storage pool information
 */
public class FederationStoragePoolResponse {
    
    private String poolId;
    private String poolName;
    private String type;
    private String storageClass;
    
    private double totalGB;
    private double usedGB;
    private double availableGB;
    private double allocatedGB;
    
    private boolean active;
    private boolean shared;
    private Set<String> accessibleNodes;
    
    private Map<String, Boolean> features;
    private Map<String, Object> performance;
    
    // Getters and setters
    public String getPoolId() { return poolId; }
    public void setPoolId(String poolId) { this.poolId = poolId; }
    
    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getStorageClass() { return storageClass; }
    public void setStorageClass(String storageClass) { this.storageClass = storageClass; }
    
    public double getTotalGB() { return totalGB; }
    public void setTotalGB(double totalGB) { this.totalGB = totalGB; }
    
    public double getUsedGB() { return usedGB; }
    public void setUsedGB(double usedGB) { this.usedGB = usedGB; }
    
    public double getAvailableGB() { return availableGB; }
    public void setAvailableGB(double availableGB) { this.availableGB = availableGB; }
    
    public double getAllocatedGB() { return allocatedGB; }
    public void setAllocatedGB(double allocatedGB) { this.allocatedGB = allocatedGB; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
    
    public Set<String> getAccessibleNodes() { return accessibleNodes; }
    public void setAccessibleNodes(Set<String> accessibleNodes) { this.accessibleNodes = accessibleNodes; }
    
    public Map<String, Boolean> getFeatures() { return features; }
    public void setFeatures(Map<String, Boolean> features) { this.features = features; }
    
    public Map<String, Object> getPerformance() { return performance; }
    public void setPerformance(Map<String, Object> performance) { this.performance = performance; }
}