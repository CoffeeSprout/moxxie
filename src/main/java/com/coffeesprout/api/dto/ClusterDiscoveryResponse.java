package com.coffeesprout.api.dto;

import java.util.List;

/**
 * Simplified cluster discovery response
 */
public class ClusterDiscoveryResponse {

    private String clusterName;
    private List<NodeInfo> nodes;
    private List<StorageInfo> storage;

    public ClusterDiscoveryResponse() {}

    public ClusterDiscoveryResponse(String clusterName, List<NodeInfo> nodes, List<StorageInfo> storage) {
        this.clusterName = clusterName;
        this.nodes = nodes;
        this.storage = storage;
    }

    // Getters and setters
    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public List<StorageInfo> getStorage() {
        return storage;
    }

    public void setStorage(List<StorageInfo> storage) {
        this.storage = storage;
    }

    public static class NodeInfo {
        private String name;
        private String status;
        private int cpuCores;
        private long totalMemoryGB;
        private long availableMemoryGB;
        private double cpuUsage;

        public NodeInfo() {}

        public NodeInfo(String name, String status, int cpuCores, long totalMemoryGB, long availableMemoryGB, double cpuUsage) {
            this.name = name;
            this.status = status;
            this.cpuCores = cpuCores;
            this.totalMemoryGB = totalMemoryGB;
            this.availableMemoryGB = availableMemoryGB;
            this.cpuUsage = cpuUsage;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getCpuCores() {
            return cpuCores;
        }

        public void setCpuCores(int cpuCores) {
            this.cpuCores = cpuCores;
        }

        public long getTotalMemoryGB() {
            return totalMemoryGB;
        }

        public void setTotalMemoryGB(long totalMemoryGB) {
            this.totalMemoryGB = totalMemoryGB;
        }

        public long getAvailableMemoryGB() {
            return availableMemoryGB;
        }

        public void setAvailableMemoryGB(long availableMemoryGB) {
            this.availableMemoryGB = availableMemoryGB;
        }

        public double getCpuUsage() {
            return cpuUsage;
        }

        public void setCpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
        }
    }

    public static class StorageInfo {
        private String name;
        private String type;
        private long totalGB;
        private long availableGB;
        private String node;

        public StorageInfo() {}

        public StorageInfo(String name, String type, long totalGB, long availableGB, String node) {
            this.name = name;
            this.type = type;
            this.totalGB = totalGB;
            this.availableGB = availableGB;
            this.node = node;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getTotalGB() {
            return totalGB;
        }

        public void setTotalGB(long totalGB) {
            this.totalGB = totalGB;
        }

        public long getAvailableGB() {
            return availableGB;
        }

        public void setAvailableGB(long availableGB) {
            this.availableGB = availableGB;
        }

        public String getNode() {
            return node;
        }

        public void setNode(String node) {
            this.node = node;
        }
    }
}
