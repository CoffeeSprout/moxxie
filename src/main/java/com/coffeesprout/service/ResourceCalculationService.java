package com.coffeesprout.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.config.MoxxieConfig;
import com.coffeesprout.federation.ClusterResources;
import com.coffeesprout.federation.NodeResources;
import com.coffeesprout.federation.ResourceRequirements;
import com.coffeesprout.federation.VMCapacity;
import com.coffeesprout.util.UnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for calculating resource availability, pressure, and efficiency metrics.
 * Focuses on visibility of current state rather than planning.
 */
@ApplicationScoped
public class ResourceCalculationService {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceCalculationService.class);

    @Inject
    MoxxieConfig config;

    // Default overcommit ratios if not configured
    private static final double DEFAULT_CPU_OVERCOMMIT = 4.0;
    private static final double DEFAULT_MEMORY_OVERCOMMIT = 1.0;
    private static final double DEFAULT_STORAGE_OVERPROVISION = 1.5;

    // Default reserve percentages for system overhead
    private static final double DEFAULT_CPU_RESERVE_PERCENT = 0.10; // 10%
    private static final double DEFAULT_MEMORY_RESERVE_PERCENT = 0.15; // 15%
    private static final double DEFAULT_STORAGE_RESERVE_PERCENT = 0.10; // 10%

    /**
     * Calculate available CPU cores considering overcommit and reserves
     */
    public double calculateAvailableCpuCores(double totalCores, double allocatedCores, double actualUsagePercent) {
        double overcommitRatio = getCpuOvercommitRatio();
        double reservePercent = getCpuReservePercent();

        double totalWithOvercommit = totalCores * overcommitRatio;
        double reserved = totalCores * reservePercent;
        double available = totalWithOvercommit - allocatedCores - reserved;

        // Don't allow negative availability
        return Math.max(0, available);
    }

    /**
     * Calculate available memory considering reserves (no overcommit for memory)
     */
    public long calculateAvailableMemoryBytes(long totalBytes, long allocatedBytes) {
        double overcommitRatio = getMemoryOvercommitRatio();
        double reservePercent = getMemoryReservePercent();

        long totalWithOvercommit = (long)(totalBytes * overcommitRatio);
        long reserved = (long)(totalBytes * reservePercent);
        long available = totalWithOvercommit - allocatedBytes - reserved;

        return Math.max(0, available);
    }

    /**
     * Calculate available storage considering thin provisioning and reserves
     */
    public long calculateAvailableStorageBytes(long totalBytes, long allocatedBytes, long actualUsedBytes) {
        double overprovisionRatio = getStorageOverprovisionRatio();
        double reservePercent = getStorageReservePercent();

        // For storage, we consider actual usage vs total capacity
        long reserved = (long)(totalBytes * reservePercent);
        long physicallyAvailable = totalBytes - actualUsedBytes - reserved;

        // But we can allocate more due to thin provisioning
        long totalAllocatable = (long)(totalBytes * overprovisionRatio);
        long allocationAvailable = totalAllocatable - allocatedBytes;

        // Return the more restrictive limit
        return Math.max(0, Math.min(physicallyAvailable, allocationAvailable));
    }

    /**
     * Calculate resource pressure (0.0 = no pressure, 1.0 = maximum pressure)
     */
    public double calculateResourcePressure(double used, double total, double threshold) {
        if (total <= 0) return 0.0;

        double utilization = used / total;

        // Apply threshold - pressure starts increasing after threshold
        if (utilization <= threshold) {
            return 0.0;
        }

        // Linear pressure increase from threshold to 100%
        return Math.min(1.0, (utilization - threshold) / (1.0 - threshold));
    }

    /**
     * Calculate CPU pressure considering overcommit
     */
    public double calculateCpuPressure(double allocatedCores, double totalCores) {
        double overcommitRatio = getCpuOvercommitRatio();
        double effectiveTotal = totalCores * overcommitRatio;

        // CPU pressure starts at 75% allocation
        return calculateResourcePressure(allocatedCores, effectiveTotal, 0.75);
    }

    /**
     * Calculate memory pressure (more sensitive than CPU)
     */
    public double calculateMemoryPressure(long usedBytes, long totalBytes) {
        // Memory pressure starts at 70% usage
        return calculateResourcePressure(usedBytes, totalBytes, 0.70);
    }

    /**
     * Calculate storage pressure based on actual usage
     */
    public double calculateStoragePressure(long usedBytes, long totalBytes) {
        // Storage pressure starts at 80% usage
        return calculateResourcePressure(usedBytes, totalBytes, 0.80);
    }

    /**
     * Calculate efficiency metric (how well resources are utilized)
     */
    public double calculateEfficiency(double allocated, double actualUsed, double total) {
        if (allocated <= 0 || total <= 0) return 0.0;

        // Efficiency is actual usage vs allocation
        double utilizationOfAllocated = actualUsed / allocated;

        // Penalize over-allocation (allocated but not used)
        double allocationRatio = allocated / total;

        // Combined efficiency score
        return utilizationOfAllocated * Math.min(1.0, 2.0 - allocationRatio);
    }

    /**
     * Aggregate node resources into cluster resources
     */
    public void aggregateNodeToCluster(ClusterResources cluster, NodeResources node) {
        if (node.getCpu() != null && cluster.getCpu() != null) {
            ClusterResources.CpuResources clusterCpu = cluster.getCpu();
            NodeResources.NodeCpuResources nodeCpu = node.getCpu();

            clusterCpu.setTotalCores(clusterCpu.getTotalCores() + nodeCpu.getPhysicalCores());
            clusterCpu.setTotalThreads(clusterCpu.getTotalThreads() + nodeCpu.getLogicalCores());
            clusterCpu.setAllocatedCores(clusterCpu.getAllocatedCores() + nodeCpu.getAllocatedCores());

            // Average the usage percentages (will be recalculated properly later)
            int nodeCount = cluster.getActiveNodes();
            double currentAvg = clusterCpu.getActualUsagePercent();
            clusterCpu.setActualUsagePercent(
                ((currentAvg * (nodeCount - 1)) + nodeCpu.getCurrentUsagePercent()) / nodeCount
            );
        }

        if (node.getMemory() != null && cluster.getMemory() != null) {
            ClusterResources.MemoryResources clusterMem = cluster.getMemory();
            NodeResources.NodeMemoryResources nodeMem = node.getMemory();

            clusterMem.setTotalBytes(clusterMem.getTotalBytes() + nodeMem.getTotalBytes());
            clusterMem.setAllocatedBytes(clusterMem.getAllocatedBytes() + nodeMem.getAllocatedBytes());
            clusterMem.setActualUsedBytes(clusterMem.getActualUsedBytes() + nodeMem.getUsedBytes());
        }

        if (node.getStorage() != null && cluster.getStorage() != null) {
            ClusterResources.StorageResources clusterStorage = cluster.getStorage();
            NodeResources.NodeStorageResources nodeStorage = node.getStorage();

            clusterStorage.setTotalBytes(clusterStorage.getTotalBytes() + nodeStorage.getTotalBytes());
            // NodeStorageResources doesn't track allocated, just used
            clusterStorage.setActualUsedBytes(clusterStorage.getActualUsedBytes() + nodeStorage.getUsedBytes());
        }

        // Update VM counts
        cluster.setTotalVMs(cluster.getTotalVMs() + node.getVmCount());
        cluster.setRunningVMs(cluster.getRunningVMs() + node.getRunningVMs());
    }

    /**
     * Finalize cluster resource calculations after aggregation
     */
    public void finalizeClusterCalculations(ClusterResources cluster) {
        // Calculate available resources
        if (cluster.getCpu() != null) {
            ClusterResources.CpuResources cpu = cluster.getCpu();
            double available = calculateAvailableCpuCores(
                cpu.getTotalCores(),
                cpu.getAllocatedCores(),
                cpu.getActualUsagePercent()
            );
            cpu.setAvailableCores((int)available);
            cpu.setOvercommitRatio(getCpuOvercommitRatio());
            cpu.setMaxOvercommit((int)(cpu.getTotalCores() * getCpuOvercommitRatio()));
        }

        if (cluster.getMemory() != null) {
            ClusterResources.MemoryResources mem = cluster.getMemory();
            long available = calculateAvailableMemoryBytes(
                mem.getTotalBytes(),
                mem.getAllocatedBytes()
            );
            mem.setAvailableBytes(available);
            mem.setOvercommitRatio(getMemoryOvercommitRatio());
            mem.setMaxOvercommit((long)(mem.getTotalBytes() * getMemoryOvercommitRatio()));
        }

        if (cluster.getStorage() != null) {
            ClusterResources.StorageResources storage = cluster.getStorage();
            long available = calculateAvailableStorageBytes(
                storage.getTotalBytes(),
                storage.getAllocatedBytes(),
                storage.getActualUsedBytes()
            );
            storage.setAvailableBytes(available);
            storage.setThinProvisioningRatio(getStorageOverprovisionRatio());
        }

        // Calculate efficiency metrics
        cluster.setCpuEfficiency(calculateEfficiency(
            cluster.getCpu().getAllocatedCores(),
            cluster.getCpu().getTotalCores() * cluster.getCpu().getActualUsagePercent() / UnitConverter.Percentage.PERCENT_MULTIPLIER,
            cluster.getCpu().getTotalCores()
        ));

        cluster.setMemoryEfficiency(calculateEfficiency(
            cluster.getMemory().getAllocatedBytes(),
            cluster.getMemory().getActualUsedBytes(),
            cluster.getMemory().getTotalBytes()
        ));

        cluster.setStorageEfficiency(calculateEfficiency(
            cluster.getStorage().getAllocatedBytes(),
            cluster.getStorage().getActualUsedBytes(),
            cluster.getStorage().getTotalBytes()
        ));
    }

    // Configuration getters with defaults

    private double getCpuOvercommitRatio() {
        return config.resources().cpu().overcommitRatio();
    }

    private double getMemoryOvercommitRatio() {
        return config.resources().memory().overcommitRatio();
    }

    private double getStorageOverprovisionRatio() {
        return config.resources().storage().overprovisionRatio();
    }

    private double getCpuReservePercent() {
        return config.resources().cpu().reservePercent() / UnitConverter.Percentage.PERCENT_MULTIPLIER;
    }

    private double getMemoryReservePercent() {
        return config.resources().memory().reservePercent() / UnitConverter.Percentage.PERCENT_MULTIPLIER;
    }

    private double getStorageReservePercent() {
        return config.resources().storage().reservePercent() / UnitConverter.Percentage.PERCENT_MULTIPLIER;
    }

    /**
     * Calculate the largest possible VM that can be created on a single node
     * Returns the node with the most available resources for a VM
     */
    public VMCapacity calculateLargestPossibleVM(List<NodeResources> nodes, ResourceRequirements requirements) {
        VMCapacity best = null;
        Map<String, VMCapacity.NodeCapacity> alternatives = new HashMap<>();
        double bestScore = 0;

        for (NodeResources node : nodes) {
            if (!"online".equals(node.getStatus())) {
                continue;
            }

            // Skip excluded nodes
            if (requirements.getExcludedNodes() != null &&
                requirements.getExcludedNodes().contains(node.getNodeId())) {
                continue;
            }

            VMCapacity capacity = calculateNodeVMCapacity(node, requirements);

            if (capacity != null) {
                double score = calculateCapacityScore(capacity, node);

                // Check if this is the best option
                if (best == null || score > bestScore) {
                    if (best != null) {
                        // Convert previous best to NodeCapacity alternative
                        VMCapacity.NodeCapacity alt = new VMCapacity.NodeCapacity();
                        alt.setNodeId(best.getNodeId());
                        alt.setNodeName(best.getNodeName());
                        alt.setMaxCpuCores(best.getMaxCpuCores());
                        alt.setMaxMemoryBytes(best.getMaxMemoryBytes());
                        alt.setMaxStorageBytes(best.getMaxStorageBytes());
                        alt.setScore(bestScore);
                        alternatives.put(best.getNodeId(), alt);
                    }
                    best = capacity;
                    bestScore = score;
                } else {
                    // Create NodeCapacity alternative
                    VMCapacity.NodeCapacity alt = new VMCapacity.NodeCapacity();
                    alt.setNodeId(capacity.getNodeId());
                    alt.setNodeName(capacity.getNodeName());
                    alt.setMaxCpuCores(capacity.getMaxCpuCores());
                    alt.setMaxMemoryBytes(capacity.getMaxMemoryBytes());
                    alt.setMaxStorageBytes(capacity.getMaxStorageBytes());
                    alt.setScore(score);
                    alternatives.put(capacity.getNodeId(), alt);
                }
            }
        }

        if (best != null) {
            best.setAlternativeNodes(alternatives);
        }

        return best;
    }

    /**
     * Calculate VM capacity for a single node
     */
    private VMCapacity calculateNodeVMCapacity(NodeResources node, ResourceRequirements requirements) {
        VMCapacity capacity = new VMCapacity();
        capacity.setNodeId(node.getNodeId());
        capacity.setNodeName(node.getNodeName());

        // Calculate maximum CPU cores
        int maxCpuCores = Integer.MAX_VALUE;
        if (node.getCpu() != null) {
            double availableCores = calculateAvailableCpuCores(
                node.getCpu().getPhysicalCores(),
                node.getCpu().getAllocatedCores(),
                node.getCpu().getCurrentUsagePercent()
            );
            maxCpuCores = (int) Math.floor(availableCores);
        }

        // Calculate maximum memory
        long maxMemoryBytes = Long.MAX_VALUE;
        if (node.getMemory() != null) {
            maxMemoryBytes = calculateAvailableMemoryBytes(
                node.getMemory().getTotalBytes(),
                node.getMemory().getAllocatedBytes()
            );
        }

        // Calculate maximum storage
        long maxStorageBytes = Long.MAX_VALUE;
        if (node.getStorage() != null) {
            maxStorageBytes = node.getStorage().getAvailableBytes();
        }

        // Apply requirements limits if specified
        if (requirements.getCpuCores() != null) {
            maxCpuCores = Math.min(maxCpuCores, requirements.getCpuCores());
        }
        if (requirements.getMemoryBytes() != null) {
            maxMemoryBytes = Math.min(maxMemoryBytes, requirements.getMemoryBytes());
        }
        if (requirements.getStorageBytes() != null) {
            maxStorageBytes = Math.min(maxStorageBytes, requirements.getStorageBytes());
        }

        // Check if node can satisfy minimum requirements
        if (maxCpuCores <= 0 || maxMemoryBytes <= 0 || maxStorageBytes <= 0) {
            return null;
        }

        capacity.setMaxCpuCores(maxCpuCores);
        capacity.setMaxMemoryBytes(maxMemoryBytes);
        capacity.setMaxStorageBytes(maxStorageBytes);
        capacity.setWithOvercommit(true);

        // Determine limiting factor
        String limitingFactor = "balanced";
        double cpuUtilization = node.getCpu() != null ?
            (double) node.getCpu().getAllocatedCores() / (node.getCpu().getPhysicalCores() * getCpuOvercommitRatio()) : 0;
        double memoryUtilization = node.getMemory() != null ?
            (double) node.getMemory().getAllocatedBytes() / node.getMemory().getTotalBytes() : 0;
        double storageUtilization = node.getStorage() != null ?
            (double) node.getStorage().getUsedBytes() / node.getStorage().getTotalBytes() : 0;

        if (cpuUtilization > memoryUtilization && cpuUtilization > storageUtilization) {
            limitingFactor = "cpu";
        } else if (memoryUtilization > storageUtilization) {
            limitingFactor = "memory";
        } else if (storageUtilization > 0.8) {
            limitingFactor = "storage";
        }

        capacity.setLimitingFactor(limitingFactor);

        return capacity;
    }


    /**
     * Calculate a score for VM capacity based on node health and resources
     */
    private double calculateCapacityScore(VMCapacity capacity, NodeResources node) {
        double score = UnitConverter.Percentage.PERCENT_MULTIPLIER;

        // Penalize based on resource pressure
        score -= node.getCpuPressure() * 20;
        score -= node.getMemoryPressure() * 30;
        score -= node.getStoragePressure() * 10;

        // Bonus for balanced resources
        if ("balanced".equals(capacity.getLimitingFactor())) {
            score += 10;
        }

        return Math.max(0, score);
    }
}
