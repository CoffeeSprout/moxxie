package com.coffeesprout.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps the configuration file (e.g., proxmox-cluster.yaml) to Java objects.
 */
public class ProxmoxClusterConfig {

    private ProxmoxDetails proxmox;
    private NetworkConfig network;
    private StorageConfig storage;

    // Getters and setters for proxmox, network, storage
    public ProxmoxDetails getProxmox() {
        return proxmox;
    }
    public void setProxmox(ProxmoxDetails proxmox) {
        this.proxmox = proxmox;
    }
    public NetworkConfig getNetwork() {
        return network;
    }
    public void setNetwork(NetworkConfig network) {
        this.network = network;
    }
    public StorageConfig getStorage() {
        return storage;
    }
    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

    public static class ProxmoxDetails {
        @JsonProperty("api_url")
        private String apiUrl;
        private String username;
        @JsonProperty("password_env")
        private String passwordEnv;
        private List<Node> nodes;

        // Getters and setters
        public String getApiUrl() {
            return apiUrl;
        }
        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }
        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }
        public String getPasswordEnv() {
            return passwordEnv;
        }
        public void setPasswordEnv(String passwordEnv) {
            this.passwordEnv = passwordEnv;
        }
        public List<Node> getNodes() {
            return nodes;
        }
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }
    }

    public static class Node {
        private String name;
        private List<String> tags;
        private String memory;  // e.g., "128GB (available: 64GB)"
        private int cpu;
        private List<?> storage;  // Can be refined later

        // Getters and setters
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public List<String> getTags() {
            return tags;
        }
        public void setTags(List<String> tags) {
            this.tags = tags;
        }
        public String getMemory() {
            return memory;
        }
        public void setMemory(String memory) {
            this.memory = memory;
        }
        public int getCpu() {
            return cpu;
        }
        public void setCpu(int cpu) {
            this.cpu = cpu;
        }
        public List<?> getStorage() {
            return storage;
        }
        public void setStorage(List<?> storage) {
            this.storage = storage;
        }
    }

    public static class NetworkConfig {
        @JsonProperty("vlan_range")
        private List<Integer> vlanRange;
        private List<String> bridges;

        // Getters and setters
        public List<Integer> getVlanRange() {
            return vlanRange;
        }
        public void setVlanRange(List<Integer> vlanRange) {
            this.vlanRange = vlanRange;
        }
        public List<String> getBridges() {
            return bridges;
        }
        public void setBridges(List<String> bridges) {
            this.bridges = bridges;
        }
    }

    public static class StorageConfig {
        @JsonProperty("default")
        private String defaultStorage;
        private List<StorageOption> options;

        // Getters and setters
        public String getDefaultStorage() {
            return defaultStorage;
        }
        public void setDefaultStorage(String defaultStorage) {
            this.defaultStorage = defaultStorage;
        }
        public List<StorageOption> getOptions() {
            return options;
        }
        public void setOptions(List<StorageOption> options) {
            this.options = options;
        }
    }

    public static class StorageOption {
        private String name;
        private String type;
        private String node;
        private String capacity;  // e.g., "2TB"
        private String available; // e.g., "1.5TB"

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
        public String getNode() {
            return node;
        }
        public void setNode(String node) {
            this.node = node;
        }
        public String getCapacity() {
            return capacity;
        }
        public void setCapacity(String capacity) {
            this.capacity = capacity;
        }
        public String getAvailable() {
            return available;
        }
        public void setAvailable(String available) {
            this.available = available;
        }
    }
}