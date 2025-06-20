package com.coffeesprout.api.dto.federation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for cluster-wide resources
 */
public class FederationClusterResourcesResponse {
    
    private String clusterId;
    private String clusterName;
    private String providerId;
    private Instant timestamp;
    
    private Map<String, Object> cpu;
    private Map<String, Object> memory;
    private Map<String, Object> storage;
    private Map<String, Object> network;
    
    private Map<String, Object> summary;
    
    // Optional detailed breakdowns
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> vms;
    
    // Getters and setters
    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }
    
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Map<String, Object> getCpu() { return cpu; }
    public void setCpu(Map<String, Object> cpu) { this.cpu = cpu; }
    
    public Map<String, Object> getMemory() { return memory; }
    public void setMemory(Map<String, Object> memory) { this.memory = memory; }
    
    public Map<String, Object> getStorage() { return storage; }
    public void setStorage(Map<String, Object> storage) { this.storage = storage; }
    
    public Map<String, Object> getNetwork() { return network; }
    public void setNetwork(Map<String, Object> network) { this.network = network; }
    
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
    
    public List<Map<String, Object>> getNodes() { return nodes; }
    public void setNodes(List<Map<String, Object>> nodes) { this.nodes = nodes; }
    
    public List<Map<String, Object>> getVms() { return vms; }
    public void setVms(List<Map<String, Object>> vms) { this.vms = vms; }
}