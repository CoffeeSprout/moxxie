package com.coffeesprout.federation;

import java.util.Map;

/**
 * Represents the largest possible VM that can be created in the cluster
 */
public class VMCapacity {

    private String nodeId;  // Node where this VM could be created
    private String nodeName;

    // Maximum resources available for a single VM
    private int maxCpuCores;
    private long maxMemoryBytes;
    private long maxStorageBytes;

    // Constraints that limit the capacity
    private String limitingFactor;  // e.g., "memory", "cpu", "storage"
    private Map<String, String> constraints;

    // Alternative capacities on different nodes
    private Map<String, NodeCapacity> alternativeNodes;

    // Whether this considers overcommit
    private boolean withOvercommit;
    private double overcommitRatioCpu;
    private double overcommitRatioMemory;

    public static class NodeCapacity {
        private String nodeId;
        private String nodeName;
        private int maxCpuCores;
        private long maxMemoryBytes;
        private long maxStorageBytes;
        private double score;  // Placement score

        // Getters and setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }

        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }

        public int getMaxCpuCores() { return maxCpuCores; }
        public void setMaxCpuCores(int maxCpuCores) { this.maxCpuCores = maxCpuCores; }

        public long getMaxMemoryBytes() { return maxMemoryBytes; }
        public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }

        public long getMaxStorageBytes() { return maxStorageBytes; }
        public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }

    // Getters and setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public int getMaxCpuCores() { return maxCpuCores; }
    public void setMaxCpuCores(int maxCpuCores) { this.maxCpuCores = maxCpuCores; }

    public long getMaxMemoryBytes() { return maxMemoryBytes; }
    public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }

    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }

    public String getLimitingFactor() { return limitingFactor; }
    public void setLimitingFactor(String limitingFactor) { this.limitingFactor = limitingFactor; }

    public Map<String, String> getConstraints() { return constraints; }
    public void setConstraints(Map<String, String> constraints) { this.constraints = constraints; }

    public Map<String, NodeCapacity> getAlternativeNodes() { return alternativeNodes; }
    public void setAlternativeNodes(Map<String, NodeCapacity> alternativeNodes) { this.alternativeNodes = alternativeNodes; }

    public boolean isWithOvercommit() { return withOvercommit; }
    public void setWithOvercommit(boolean withOvercommit) { this.withOvercommit = withOvercommit; }

    public double getOvercommitRatioCpu() { return overcommitRatioCpu; }
    public void setOvercommitRatioCpu(double overcommitRatioCpu) { this.overcommitRatioCpu = overcommitRatioCpu; }

    public double getOvercommitRatioMemory() { return overcommitRatioMemory; }
    public void setOvercommitRatioMemory(double overcommitRatioMemory) { this.overcommitRatioMemory = overcommitRatioMemory; }
}
