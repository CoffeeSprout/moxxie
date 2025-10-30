package com.coffeesprout.federation;

import java.time.Instant;
import java.util.Map;

/**
 * Represents cluster-wide resource summary with detailed breakdowns
 */
public class ClusterResources {

    private String clusterId;
    private String clusterName;
    private String providerId;
    private Instant timestamp;

    // CPU Resources
    private CpuResources cpu;

    // Memory Resources
    private MemoryResources memory;

    // Storage Resources
    private StorageResources storage;

    // Network Resources
    private NetworkResources network;

    // Additional metrics
    private int totalNodes;
    private int activeNodes;
    private int totalVMs;
    private int runningVMs;

    // Resource efficiency metrics
    private double cpuEfficiency;  // Actual usage vs allocated
    private double memoryEfficiency;
    private double storageEfficiency;

    // Provider-specific metadata
    private Map<String, Object> metadata;

    public static class CpuResources {
        private int totalCores;          // Physical CPU cores
        private int totalThreads;        // Logical CPUs (with HT)
        private double totalGHz;         // Total CPU capacity in GHz

        private int allocatedCores;      // Sum of all VM vCPUs
        private double allocatedGHz;     // Allocated CPU in GHz

        private double actualUsagePercent;  // Current CPU usage %
        private double actualUsageGHz;      // Current CPU usage in GHz

        private int availableCores;      // Cores available for new VMs
        private double availableGHz;     // GHz available for new VMs

        private double overcommitRatio;  // Allocated/Physical ratio
        private int maxOvercommit;       // Maximum allowed overcommit

        // Getters and setters
        public int getTotalCores() { return totalCores; }
        public void setTotalCores(int totalCores) { this.totalCores = totalCores; }

        public int getTotalThreads() { return totalThreads; }
        public void setTotalThreads(int totalThreads) { this.totalThreads = totalThreads; }

        public double getTotalGHz() { return totalGHz; }
        public void setTotalGHz(double totalGHz) { this.totalGHz = totalGHz; }

        public int getAllocatedCores() { return allocatedCores; }
        public void setAllocatedCores(int allocatedCores) { this.allocatedCores = allocatedCores; }

        public double getAllocatedGHz() { return allocatedGHz; }
        public void setAllocatedGHz(double allocatedGHz) { this.allocatedGHz = allocatedGHz; }

        public double getActualUsagePercent() { return actualUsagePercent; }
        public void setActualUsagePercent(double actualUsagePercent) { this.actualUsagePercent = actualUsagePercent; }

        public double getActualUsageGHz() { return actualUsageGHz; }
        public void setActualUsageGHz(double actualUsageGHz) { this.actualUsageGHz = actualUsageGHz; }

        public int getAvailableCores() { return availableCores; }
        public void setAvailableCores(int availableCores) { this.availableCores = availableCores; }

        public double getAvailableGHz() { return availableGHz; }
        public void setAvailableGHz(double availableGHz) { this.availableGHz = availableGHz; }

        public double getOvercommitRatio() { return overcommitRatio; }
        public void setOvercommitRatio(double overcommitRatio) { this.overcommitRatio = overcommitRatio; }

        public int getMaxOvercommit() { return maxOvercommit; }
        public void setMaxOvercommit(int maxOvercommit) { this.maxOvercommit = maxOvercommit; }
    }

    public static class MemoryResources {
        private long totalBytes;         // Physical RAM
        private long allocatedBytes;     // Sum of all VM RAM
        private long actualUsedBytes;    // Current RAM usage
        private long availableBytes;     // RAM available for new VMs

        private long reservedBytes;      // Reserved for system/hypervisor
        private long bufferCacheBytes;   // Buffer/cache usage

        private double overcommitRatio;  // Allocated/Physical ratio
        private double maxOvercommit;    // Maximum allowed overcommit

        private long swapTotalBytes;     // Total swap space
        private long swapUsedBytes;      // Used swap space

        // Getters and setters
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

        public long getAllocatedBytes() { return allocatedBytes; }
        public void setAllocatedBytes(long allocatedBytes) { this.allocatedBytes = allocatedBytes; }

        public long getActualUsedBytes() { return actualUsedBytes; }
        public void setActualUsedBytes(long actualUsedBytes) { this.actualUsedBytes = actualUsedBytes; }

        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }

        public long getReservedBytes() { return reservedBytes; }
        public void setReservedBytes(long reservedBytes) { this.reservedBytes = reservedBytes; }

        public long getBufferCacheBytes() { return bufferCacheBytes; }
        public void setBufferCacheBytes(long bufferCacheBytes) { this.bufferCacheBytes = bufferCacheBytes; }

        public double getOvercommitRatio() { return overcommitRatio; }
        public void setOvercommitRatio(double overcommitRatio) { this.overcommitRatio = overcommitRatio; }

        public double getMaxOvercommit() { return maxOvercommit; }
        public void setMaxOvercommit(double maxOvercommit) { this.maxOvercommit = maxOvercommit; }

        public long getSwapTotalBytes() { return swapTotalBytes; }
        public void setSwapTotalBytes(long swapTotalBytes) { this.swapTotalBytes = swapTotalBytes; }

        public long getSwapUsedBytes() { return swapUsedBytes; }
        public void setSwapUsedBytes(long swapUsedBytes) { this.swapUsedBytes = swapUsedBytes; }
    }

    public static class StorageResources {
        private long totalBytes;         // Total storage capacity
        private long allocatedBytes;     // Sum of all VM disks
        private long actualUsedBytes;    // Actual disk usage
        private long availableBytes;     // Available for new VMs

        private long reservedBytes;      // Reserved space
        private double thinProvisioningRatio;  // For thin provisioning

        private int totalPools;
        private int activePools;

        // Per-type breakdown
        private Map<String, StorageTypeInfo> byType;  // SSD, HDD, NVMe, etc.

        // Getters and setters
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

        public long getAllocatedBytes() { return allocatedBytes; }
        public void setAllocatedBytes(long allocatedBytes) { this.allocatedBytes = allocatedBytes; }

        public long getActualUsedBytes() { return actualUsedBytes; }
        public void setActualUsedBytes(long actualUsedBytes) { this.actualUsedBytes = actualUsedBytes; }

        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }

        public long getReservedBytes() { return reservedBytes; }
        public void setReservedBytes(long reservedBytes) { this.reservedBytes = reservedBytes; }

        public double getThinProvisioningRatio() { return thinProvisioningRatio; }
        public void setThinProvisioningRatio(double thinProvisioningRatio) { this.thinProvisioningRatio = thinProvisioningRatio; }

        public int getTotalPools() { return totalPools; }
        public void setTotalPools(int totalPools) { this.totalPools = totalPools; }

        public int getActivePools() { return activePools; }
        public void setActivePools(int activePools) { this.activePools = activePools; }

        public Map<String, StorageTypeInfo> getByType() { return byType; }
        public void setByType(Map<String, StorageTypeInfo> byType) { this.byType = byType; }
    }

    public static class NetworkResources {
        private long totalBandwidthBps;     // Total network capacity
        private long usedBandwidthBps;      // Current usage
        private long availableBandwidthBps;  // Available bandwidth

        private int totalVlans;
        private int usedVlans;
        private int availableVlans;

        private int totalIpAddresses;
        private int usedIpAddresses;
        private int availableIpAddresses;

        // Getters and setters
        public long getTotalBandwidthBps() { return totalBandwidthBps; }
        public void setTotalBandwidthBps(long totalBandwidthBps) { this.totalBandwidthBps = totalBandwidthBps; }

        public long getUsedBandwidthBps() { return usedBandwidthBps; }
        public void setUsedBandwidthBps(long usedBandwidthBps) { this.usedBandwidthBps = usedBandwidthBps; }

        public long getAvailableBandwidthBps() { return availableBandwidthBps; }
        public void setAvailableBandwidthBps(long availableBandwidthBps) { this.availableBandwidthBps = availableBandwidthBps; }

        public int getTotalVlans() { return totalVlans; }
        public void setTotalVlans(int totalVlans) { this.totalVlans = totalVlans; }

        public int getUsedVlans() { return usedVlans; }
        public void setUsedVlans(int usedVlans) { this.usedVlans = usedVlans; }

        public int getAvailableVlans() { return availableVlans; }
        public void setAvailableVlans(int availableVlans) { this.availableVlans = availableVlans; }

        public int getTotalIpAddresses() { return totalIpAddresses; }
        public void setTotalIpAddresses(int totalIpAddresses) { this.totalIpAddresses = totalIpAddresses; }

        public int getUsedIpAddresses() { return usedIpAddresses; }
        public void setUsedIpAddresses(int usedIpAddresses) { this.usedIpAddresses = usedIpAddresses; }

        public int getAvailableIpAddresses() { return availableIpAddresses; }
        public void setAvailableIpAddresses(int availableIpAddresses) { this.availableIpAddresses = availableIpAddresses; }
    }

    public static class StorageTypeInfo {
        private String type;  // SSD, HDD, NVMe
        private long totalBytes;
        private long usedBytes;
        private long availableBytes;
        private int poolCount;

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

        public long getUsedBytes() { return usedBytes; }
        public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

        public long getAvailableBytes() { return availableBytes; }
        public void setAvailableBytes(long availableBytes) { this.availableBytes = availableBytes; }

        public int getPoolCount() { return poolCount; }
        public void setPoolCount(int poolCount) { this.poolCount = poolCount; }
    }

    // Main class getters and setters
    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public CpuResources getCpu() { return cpu; }
    public void setCpu(CpuResources cpu) { this.cpu = cpu; }

    public MemoryResources getMemory() { return memory; }
    public void setMemory(MemoryResources memory) { this.memory = memory; }

    public StorageResources getStorage() { return storage; }
    public void setStorage(StorageResources storage) { this.storage = storage; }

    public NetworkResources getNetwork() { return network; }
    public void setNetwork(NetworkResources network) { this.network = network; }

    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public int getActiveNodes() { return activeNodes; }
    public void setActiveNodes(int activeNodes) { this.activeNodes = activeNodes; }

    public int getTotalVMs() { return totalVMs; }
    public void setTotalVMs(int totalVMs) { this.totalVMs = totalVMs; }

    public int getRunningVMs() { return runningVMs; }
    public void setRunningVMs(int runningVMs) { this.runningVMs = runningVMs; }

    public double getCpuEfficiency() { return cpuEfficiency; }
    public void setCpuEfficiency(double cpuEfficiency) { this.cpuEfficiency = cpuEfficiency; }

    public double getMemoryEfficiency() { return memoryEfficiency; }
    public void setMemoryEfficiency(double memoryEfficiency) { this.memoryEfficiency = memoryEfficiency; }

    public double getStorageEfficiency() { return storageEfficiency; }
    public void setStorageEfficiency(double storageEfficiency) { this.storageEfficiency = storageEfficiency; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
