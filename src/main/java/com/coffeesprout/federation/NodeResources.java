package com.coffeesprout.federation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents resources for a specific node in the cluster
 */
public class NodeResources {
    
    private String nodeId;
    private String nodeName;
    private String status;  // online, offline, maintenance
    private Instant timestamp;
    
    // CPU Resources for this node
    private NodeCpuResources cpu;
    
    // Memory Resources for this node
    private NodeMemoryResources memory;
    
    // Storage Resources accessible from this node
    private NodeStorageResources storage;
    
    // Network Resources for this node
    private NodeNetworkResources network;
    
    // VMs running on this node
    private int vmCount;
    private int runningVMs;
    private List<String> vmIds;
    
    // Node-specific metadata
    private Map<String, Object> metadata;
    private Map<String, String> labels;
    
    // Resource pressure indicators
    private double cpuPressure;     // 0-1 scale
    private double memoryPressure;  // 0-1 scale
    private double storagePressure; // 0-1 scale
    
    public static class NodeCpuResources {
        private int physicalCores;
        private int logicalCores;  // With hyperthreading
        private double cpuFrequencyGHz;
        private String cpuModel;
        
        private int allocatedCores;  // Sum of VM vCPUs on this node
        private double currentUsagePercent;
        private double averageUsagePercent;  // Over last hour
        
        private int availableCores;  // Considering overcommit rules
        private double loadAverage1min;
        private double loadAverage5min;
        private double loadAverage15min;
        
        // Getters and setters
        public int getPhysicalCores() { return physicalCores; }
        public void setPhysicalCores(int physicalCores) { this.physicalCores = physicalCores; }
        
        public int getLogicalCores() { return logicalCores; }
        public void setLogicalCores(int logicalCores) { this.logicalCores = logicalCores; }
        
        public double getCpuFrequencyGHz() { return cpuFrequencyGHz; }
        public void setCpuFrequencyGHz(double cpuFrequencyGHz) { this.cpuFrequencyGHz = cpuFrequencyGHz; }
        
        public String getCpuModel() { return cpuModel; }
        public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }
        
        public int getAllocatedCores() { return allocatedCores; }
        public void setAllocatedCores(int allocatedCores) { this.allocatedCores = allocatedCores; }
        
        public double getCurrentUsagePercent() { return currentUsagePercent; }
        public void setCurrentUsagePercent(double currentUsagePercent) { this.currentUsagePercent = currentUsagePercent; }
        
        public double getAverageUsagePercent() { return averageUsagePercent; }
        public void setAverageUsagePercent(double averageUsagePercent) { this.averageUsagePercent = averageUsagePercent; }
        
        public int getAvailableCores() { return availableCores; }
        public void setAvailableCores(int availableCores) { this.availableCores = availableCores; }
        
        public double getLoadAverage1min() { return loadAverage1min; }
        public void setLoadAverage1min(double loadAverage1min) { this.loadAverage1min = loadAverage1min; }
        
        public double getLoadAverage5min() { return loadAverage5min; }
        public void setLoadAverage5min(double loadAverage5min) { this.loadAverage5min = loadAverage5min; }
        
        public double getLoadAverage15min() { return loadAverage15min; }
        public void setLoadAverage15min(double loadAverage15min) { this.loadAverage15min = loadAverage15min; }
    }
    
    public static class NodeMemoryResources {
        private long totalBytes;
        private long allocatedBytes;  // Sum of VM memory on this node
        private long usedBytes;       // Actual memory usage
        private long freeBytes;       // Free physical memory
        private long availableBytes;  // Available for new VMs
        
        private long bufferBytes;
        private long cacheBytes;
        private long swapTotalBytes;
        private long swapUsedBytes;
        
        private double usagePercent;
        private double commitRatio;   // Allocated/Total
        
        // Getters and setters
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public long getAllocatedBytes() { return allocatedBytes; }
        public void setAllocatedBytes(long allocatedBytes) { this.allocatedBytes = allocatedBytes; }
        
        public long getUsedBytes() { return usedBytes; }
        public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }
        
        public long getFreeBytes() { return freeBytes; }
        public void setFreeBytes(long freeBytes) { this.freeBytes = freeBytes; }
        
        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }
        
        public long getBufferBytes() { return bufferBytes; }
        public void setBufferBytes(long bufferBytes) { this.bufferBytes = bufferBytes; }
        
        public long getCacheBytes() { return cacheBytes; }
        public void setCacheBytes(long cacheBytes) { this.cacheBytes = cacheBytes; }
        
        public long getSwapTotalBytes() { return swapTotalBytes; }
        public void setSwapTotalBytes(long swapTotalBytes) { this.swapTotalBytes = swapTotalBytes; }
        
        public long getSwapUsedBytes() { return swapUsedBytes; }
        public void setSwapUsedBytes(long swapUsedBytes) { this.swapUsedBytes = swapUsedBytes; }
        
        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }
        
        public double getCommitRatio() { return commitRatio; }
        public void setCommitRatio(double commitRatio) { this.commitRatio = commitRatio; }
    }
    
    public static class NodeStorageResources {
        private List<NodeStoragePool> pools;
        private long totalBytes;
        private long usedBytes;
        private long availableBytes;
        
        // Getters and setters
        public List<NodeStoragePool> getPools() { return pools; }
        public void setPools(List<NodeStoragePool> pools) { this.pools = pools; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public long getUsedBytes() { return usedBytes; }
        public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }
        
        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }
    }
    
    public static class NodeStoragePool {
        private String poolId;
        private String poolName;
        private String type;  // local, nfs, ceph, etc.
        private String storageClass;  // SSD, HDD, NVMe
        
        private long totalBytes;
        private long usedBytes;
        private long availableBytes;
        private long allocatedBytes;  // Sum of VM disks
        
        private boolean active;
        private boolean shared;  // Shared across nodes
        
        // Getters and setters
        public String getPoolId() { return poolId; }
        public void setPoolId(String poolId) { this.poolId = poolId; }
        
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getStorageClass() { return storageClass; }
        public void setStorageClass(String storageClass) { this.storageClass = storageClass; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public long getUsedBytes() { return usedBytes; }
        public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }
        
        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }
        
        public long getAllocatedBytes() { return allocatedBytes; }
        public void setAllocatedBytes(long allocatedBytes) { this.allocatedBytes = allocatedBytes; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public boolean isShared() { return shared; }
        public void setShared(boolean shared) { this.shared = shared; }
    }
    
    public static class NodeNetworkResources {
        private List<NetworkInterface> interfaces;
        private long totalBandwidthBps;
        private long usedBandwidthBps;
        
        // Getters and setters
        public List<NetworkInterface> getInterfaces() { return interfaces; }
        public void setInterfaces(List<NetworkInterface> interfaces) { this.interfaces = interfaces; }
        
        public long getTotalBandwidthBps() { return totalBandwidthBps; }
        public void setTotalBandwidthBps(long totalBandwidthBps) { this.totalBandwidthBps = totalBandwidthBps; }
        
        public long getUsedBandwidthBps() { return usedBandwidthBps; }
        public void setUsedBandwidthBps(long usedBandwidthBps) { this.usedBandwidthBps = usedBandwidthBps; }
    }
    
    public static class NetworkInterface {
        private String name;
        private String type;  // physical, virtual, bridge
        private long speedBps;
        private boolean active;
        private String ipAddress;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public long getSpeedBps() { return speedBps; }
        public void setSpeedBps(long speedBps) { this.speedBps = speedBps; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    }
    
    // Main class getters and setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public NodeCpuResources getCpu() { return cpu; }
    public void setCpu(NodeCpuResources cpu) { this.cpu = cpu; }
    
    public NodeMemoryResources getMemory() { return memory; }
    public void setMemory(NodeMemoryResources memory) { this.memory = memory; }
    
    public NodeStorageResources getStorage() { return storage; }
    public void setStorage(NodeStorageResources storage) { this.storage = storage; }
    
    public NodeNetworkResources getNetwork() { return network; }
    public void setNetwork(NodeNetworkResources network) { this.network = network; }
    
    public int getVmCount() { return vmCount; }
    public void setVmCount(int vmCount) { this.vmCount = vmCount; }
    
    public int getRunningVMs() { return runningVMs; }
    public void setRunningVMs(int runningVMs) { this.runningVMs = runningVMs; }
    
    public List<String> getVmIds() { return vmIds; }
    public void setVmIds(List<String> vmIds) { this.vmIds = vmIds; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    
    public double getCpuPressure() { return cpuPressure; }
    public void setCpuPressure(double cpuPressure) { this.cpuPressure = cpuPressure; }
    
    public double getMemoryPressure() { return memoryPressure; }
    public void setMemoryPressure(double memoryPressure) { this.memoryPressure = memoryPressure; }
    
    public double getStoragePressure() { return storagePressure; }
    public void setStoragePressure(double storagePressure) { this.storagePressure = storagePressure; }
}