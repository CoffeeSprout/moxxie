package com.coffeesprout.api.dto.federation;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for VM capacity calculation
 */
public class FederationVMCapacityResponse {

    private String nodeId;
    private String nodeName;

    private int maxCpuCores;
    private double maxMemoryGB;
    private double maxStorageGB;

    private String limitingFactor;
    private Map<String, String> constraints;

    private boolean withOvercommit;
    private double overcommitRatioCpu;
    private double overcommitRatioMemory;

    private List<Map<String, Object>> alternatives;

    // Getters and setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public int getMaxCpuCores() { return maxCpuCores; }
    public void setMaxCpuCores(int maxCpuCores) { this.maxCpuCores = maxCpuCores; }

    public double getMaxMemoryGB() { return maxMemoryGB; }
    public void setMaxMemoryGB(double maxMemoryGB) { this.maxMemoryGB = maxMemoryGB; }

    public double getMaxStorageGB() { return maxStorageGB; }
    public void setMaxStorageGB(double maxStorageGB) { this.maxStorageGB = maxStorageGB; }

    public String getLimitingFactor() { return limitingFactor; }
    public void setLimitingFactor(String limitingFactor) { this.limitingFactor = limitingFactor; }

    public Map<String, String> getConstraints() { return constraints; }
    public void setConstraints(Map<String, String> constraints) { this.constraints = constraints; }

    public boolean isWithOvercommit() { return withOvercommit; }
    public void setWithOvercommit(boolean withOvercommit) { this.withOvercommit = withOvercommit; }

    public double getOvercommitRatioCpu() { return overcommitRatioCpu; }
    public void setOvercommitRatioCpu(double overcommitRatioCpu) { this.overcommitRatioCpu = overcommitRatioCpu; }

    public double getOvercommitRatioMemory() { return overcommitRatioMemory; }
    public void setOvercommitRatioMemory(double overcommitRatioMemory) { this.overcommitRatioMemory = overcommitRatioMemory; }

    public List<Map<String, Object>> getAlternatives() { return alternatives; }
    public void setAlternatives(List<Map<String, Object>> alternatives) { this.alternatives = alternatives; }
}
