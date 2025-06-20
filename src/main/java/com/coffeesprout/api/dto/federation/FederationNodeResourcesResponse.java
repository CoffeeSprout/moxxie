package com.coffeesprout.api.dto.federation;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for node resources
 */
public class FederationNodeResourcesResponse {
    
    private String nodeId;
    private String nodeName;
    private String status;
    private Instant timestamp;
    
    private Map<String, Object> cpu;
    private Map<String, Object> memory;
    private Map<String, Object> storage;
    private Map<String, Object> network;
    
    private Map<String, Double> resourcePressure;
    private Map<String, Integer> vmInfo;
    
    // Getters and setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
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
    
    public Map<String, Double> getResourcePressure() { return resourcePressure; }
    public void setResourcePressure(Map<String, Double> resourcePressure) { this.resourcePressure = resourcePressure; }
    
    public Map<String, Integer> getVmInfo() { return vmInfo; }
    public void setVmInfo(Map<String, Integer> vmInfo) { this.vmInfo = vmInfo; }
}