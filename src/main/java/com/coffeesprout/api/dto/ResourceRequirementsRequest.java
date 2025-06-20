package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ResourceRequirementsRequest {
    
    @JsonProperty("cpu_cores")
    @Min(1)
    private Integer cpuCores;
    
    @JsonProperty("memory_gb")
    @Min(1)
    private Double memoryGB;
    
    @JsonProperty("storage_gb")
    @Min(1)
    private Double storageGB;
    
    @JsonProperty("storage_type")
    private String storageType;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("high_availability")
    private Boolean highAvailability;
    
    @JsonProperty("preferred_nodes")
    private List<String> preferredNodes;
    
    @JsonProperty("excluded_nodes")
    private List<String> excludedNodes;

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Double getMemoryGB() {
        return memoryGB;
    }

    public void setMemoryGB(Double memoryGB) {
        this.memoryGB = memoryGB;
    }

    public Double getStorageGB() {
        return storageGB;
    }

    public void setStorageGB(Double storageGB) {
        this.storageGB = storageGB;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getHighAvailability() {
        return highAvailability;
    }

    public void setHighAvailability(Boolean highAvailability) {
        this.highAvailability = highAvailability;
    }

    public List<String> getPreferredNodes() {
        return preferredNodes;
    }

    public void setPreferredNodes(List<String> preferredNodes) {
        this.preferredNodes = preferredNodes;
    }

    public List<String> getExcludedNodes() {
        return excludedNodes;
    }

    public void setExcludedNodes(List<String> excludedNodes) {
        this.excludedNodes = excludedNodes;
    }
}