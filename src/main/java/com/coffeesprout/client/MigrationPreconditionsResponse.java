package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response from Proxmox migration precondition check
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigrationPreconditionsResponse {
    
    @JsonProperty("allowed_nodes")
    private List<String> allowedNodes;
    
    @JsonProperty("not_allowed_nodes")
    private Map<String, Object> notAllowedNodes;
    
    @JsonProperty("local_disks")
    private List<Object> localDisks;
    
    @JsonProperty("local_resources")
    private List<String> localResources;
    
    @JsonProperty("mapped-resources")
    private List<String> mappedResources;
    
    @JsonProperty("mapped-resource-info")
    private Map<String, Object> mappedResourceInfo;
    
    @JsonProperty("running")
    private Boolean running;
    
    // Getters and setters
    public List<String> getAllowedNodes() {
        return allowedNodes;
    }
    
    public void setAllowedNodes(List<String> allowedNodes) {
        this.allowedNodes = allowedNodes;
    }
    
    public Map<String, Object> getNotAllowedNodes() {
        return notAllowedNodes;
    }
    
    public void setNotAllowedNodes(Map<String, Object> notAllowedNodes) {
        this.notAllowedNodes = notAllowedNodes;
    }
    
    public List<Object> getLocalDisks() {
        return localDisks;
    }
    
    public void setLocalDisks(List<Object> localDisks) {
        this.localDisks = localDisks;
    }
    
    public List<String> getLocalResources() {
        return localResources;
    }
    
    public void setLocalResources(List<String> localResources) {
        this.localResources = localResources;
    }
    
    public List<String> getMappedResources() {
        return mappedResources;
    }
    
    public void setMappedResources(List<String> mappedResources) {
        this.mappedResources = mappedResources;
    }
    
    public Map<String, Object> getMappedResourceInfo() {
        return mappedResourceInfo;
    }
    
    public void setMappedResourceInfo(Map<String, Object> mappedResourceInfo) {
        this.mappedResourceInfo = mappedResourceInfo;
    }
    
    public Boolean getRunning() {
        return running;
    }
    
    public void setRunning(Boolean running) {
        this.running = running;
    }
}