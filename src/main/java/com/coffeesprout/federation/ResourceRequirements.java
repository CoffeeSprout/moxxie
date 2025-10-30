package com.coffeesprout.federation;

import java.util.Map;
import java.util.Set;

/**
 * Represents resource requirements for VM placement or capacity calculations
 */
public class ResourceRequirements {

    // CPU requirements
    private Integer cpuCores;
    private Double cpuGHz;
    private String cpuType;  // e.g., "host", "x86_64", "arm64"
    private Boolean cpuPinning;

    // Memory requirements
    private Long memoryBytes;
    private Boolean hugepages;
    private Boolean numa;

    // Storage requirements
    private Long storageBytes;
    private String storageType;  // e.g., "SSD", "HDD", "NVMe"
    private String storageClass;  // e.g., "fast", "standard", "archive"
    private Boolean thinProvisioning;

    // Network requirements
    private Long bandwidthBps;
    private Set<String> requiredVlans;
    private Integer ipAddressCount;

    // Placement constraints
    private Set<String> preferredNodes;
    private Set<String> excludedNodes;
    private Map<String, String> requiredLabels;
    private String location;
    private String availabilityZone;

    // Performance requirements
    private Integer iopsRequired;
    private Double latencyMaxMs;

    // Cost constraints
    private Double maxCostPerHour;
    private String costTier;  // e.g., "economy", "standard", "premium"

    // Redundancy requirements
    private Boolean highAvailability;
    private Integer replicaCount;
    private String antiAffinityGroup;

    // Builder pattern for easy construction
    public static class Builder {
        private ResourceRequirements requirements = new ResourceRequirements();

        public Builder cpuCores(int cores) {
            requirements.cpuCores = cores;
            return this;
        }

        public Builder memoryGB(long memoryGB) {
            requirements.memoryBytes = memoryGB * 1024L * 1024L * 1024L;
            return this;
        }

        public Builder memoryBytes(long bytes) {
            requirements.memoryBytes = bytes;
            return this;
        }

        public Builder storageGB(long storageGB) {
            requirements.storageBytes = storageGB * 1024L * 1024L * 1024L;
            return this;
        }

        public Builder storageBytes(long bytes) {
            requirements.storageBytes = bytes;
            return this;
        }

        public Builder storageType(String type) {
            requirements.storageType = type;
            return this;
        }

        public Builder location(String location) {
            requirements.location = location;
            return this;
        }

        public Builder highAvailability(boolean ha) {
            requirements.highAvailability = ha;
            return this;
        }

        public Builder preferredNodes(Set<String> nodes) {
            requirements.preferredNodes = nodes;
            return this;
        }

        public Builder excludedNodes(Set<String> nodes) {
            requirements.excludedNodes = nodes;
            return this;
        }

        public Builder maxCostPerHour(double cost) {
            requirements.maxCostPerHour = cost;
            return this;
        }

        public ResourceRequirements build() {
            return requirements;
        }
    }

    // Getters and setters
    public Integer getCpuCores() { return cpuCores; }
    public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }

    public Double getCpuGHz() { return cpuGHz; }
    public void setCpuGHz(Double cpuGHz) { this.cpuGHz = cpuGHz; }

    public String getCpuType() { return cpuType; }
    public void setCpuType(String cpuType) { this.cpuType = cpuType; }

    public Boolean getCpuPinning() { return cpuPinning; }
    public void setCpuPinning(Boolean cpuPinning) { this.cpuPinning = cpuPinning; }

    public Long getMemoryBytes() { return memoryBytes; }
    public void setMemoryBytes(Long memoryBytes) { this.memoryBytes = memoryBytes; }

    public Boolean getHugepages() { return hugepages; }
    public void setHugepages(Boolean hugepages) { this.hugepages = hugepages; }

    public Boolean getNuma() { return numa; }
    public void setNuma(Boolean numa) { this.numa = numa; }

    public Long getStorageBytes() { return storageBytes; }
    public void setStorageBytes(Long storageBytes) { this.storageBytes = storageBytes; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public String getStorageClass() { return storageClass; }
    public void setStorageClass(String storageClass) { this.storageClass = storageClass; }

    public Boolean getThinProvisioning() { return thinProvisioning; }
    public void setThinProvisioning(Boolean thinProvisioning) { this.thinProvisioning = thinProvisioning; }

    public Long getBandwidthBps() { return bandwidthBps; }
    public void setBandwidthBps(Long bandwidthBps) { this.bandwidthBps = bandwidthBps; }

    public Set<String> getRequiredVlans() { return requiredVlans; }
    public void setRequiredVlans(Set<String> requiredVlans) { this.requiredVlans = requiredVlans; }

    public Integer getIpAddressCount() { return ipAddressCount; }
    public void setIpAddressCount(Integer ipAddressCount) { this.ipAddressCount = ipAddressCount; }

    public Set<String> getPreferredNodes() { return preferredNodes; }
    public void setPreferredNodes(Set<String> preferredNodes) { this.preferredNodes = preferredNodes; }

    public Set<String> getExcludedNodes() { return excludedNodes; }
    public void setExcludedNodes(Set<String> excludedNodes) { this.excludedNodes = excludedNodes; }

    public Map<String, String> getRequiredLabels() { return requiredLabels; }
    public void setRequiredLabels(Map<String, String> requiredLabels) { this.requiredLabels = requiredLabels; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAvailabilityZone() { return availabilityZone; }
    public void setAvailabilityZone(String availabilityZone) { this.availabilityZone = availabilityZone; }

    public Integer getIopsRequired() { return iopsRequired; }
    public void setIopsRequired(Integer iopsRequired) { this.iopsRequired = iopsRequired; }

    public Double getLatencyMaxMs() { return latencyMaxMs; }
    public void setLatencyMaxMs(Double latencyMaxMs) { this.latencyMaxMs = latencyMaxMs; }

    public Double getMaxCostPerHour() { return maxCostPerHour; }
    public void setMaxCostPerHour(Double maxCostPerHour) { this.maxCostPerHour = maxCostPerHour; }

    public String getCostTier() { return costTier; }
    public void setCostTier(String costTier) { this.costTier = costTier; }

    public Boolean getHighAvailability() { return highAvailability; }
    public void setHighAvailability(Boolean highAvailability) { this.highAvailability = highAvailability; }

    public Integer getReplicaCount() { return replicaCount; }
    public void setReplicaCount(Integer replicaCount) { this.replicaCount = replicaCount; }

    public String getAntiAffinityGroup() { return antiAffinityGroup; }
    public void setAntiAffinityGroup(String antiAffinityGroup) { this.antiAffinityGroup = antiAffinityGroup; }
}
