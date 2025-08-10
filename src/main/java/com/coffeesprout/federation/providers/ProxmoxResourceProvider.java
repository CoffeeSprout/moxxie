package com.coffeesprout.federation.providers;

import com.coffeesprout.client.*;
import com.coffeesprout.federation.*;
import com.coffeesprout.service.NodeService;
import com.coffeesprout.service.ResourceCalculationService;
import com.coffeesprout.service.VMService;
import com.coffeesprout.service.TicketManager;
import com.coffeesprout.api.dto.VMResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Proxmox implementation of the ResourceProvider interface
 */
@ApplicationScoped
public class ProxmoxResourceProvider implements ResourceProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProxmoxResourceProvider.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    NodeService nodeService;
    
    @Inject
    VMService vmService;
    
    @Inject
    ResourceCalculationService calculationService;
    
    @Inject
    TicketManager ticketManager;
    
    @ConfigProperty(name = "moxxie.provider.proxmox.id", defaultValue = "proxmox-main")
    String providerId;
    
    @ConfigProperty(name = "moxxie.provider.proxmox.name", defaultValue = "Main Proxmox Cluster")
    String providerName;
    
    @ConfigProperty(name = "moxxie.provider.proxmox.location", defaultValue = "default")
    String location;
    
    // Cache for performance
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile long lastRefreshTime = 0;
    private static final long CACHE_TTL_MS = 60_000; // 1 minute
    
    @Override
    public String getProviderId() {
        return providerId;
    }
    
    @Override
    public String getProviderName() {
        return providerName;
    }
    
    @Override
    public String getProviderType() {
        return "proxmox";
    }
    
    @Override
    public String getLocation() {
        return location;
    }
    
    @Override
    public CompletableFuture<Boolean> isAvailable() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StatusResponse status = proxmoxClient.getStatus();
                return status != null;
            } catch (Exception e) {
                LOG.error("Failed to check Proxmox availability", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<ClusterResources> getClusterResources() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get all nodes
                List<Node> nodes = nodeService.listNodes(null);
                
                ClusterResources resources = new ClusterResources();
                resources.setClusterId(providerId);
                resources.setClusterName(providerName);
                resources.setProviderId(providerId);
                resources.setTimestamp(Instant.now());
                
                // Initialize resource objects
                ClusterResources.CpuResources cpu = new ClusterResources.CpuResources();
                ClusterResources.MemoryResources memory = new ClusterResources.MemoryResources();
                ClusterResources.StorageResources storage = new ClusterResources.StorageResources();
                
                // Aggregate resources from all nodes
                int totalCores = 0;
                long totalMemory = 0;
                long totalStorage = 0;
                long usedMemory = 0;
                long usedStorage = 0;
                
                for (Node node : nodes) {
                    NodeStatus nodeStatus = nodeService.getNodeStatus(node.getName(), null);
                    if (nodeStatus != null) {
                        // CPU
                        if (nodeStatus.getCpuInfo() != null) {
                            totalCores += nodeStatus.getCpuInfo().getCpus();
                        }
                        
                        // Memory
                        if (nodeStatus.getMemory() != null) {
                            totalMemory += nodeStatus.getMemory().getTotal();
                            usedMemory += nodeStatus.getMemory().getUsed();
                        }
                    }
                    
                    // Storage
                    List<com.coffeesprout.client.StoragePool> pools = nodeService.getNodeStorage(node.getName(), null);
                    for (com.coffeesprout.client.StoragePool pool : pools) {
                        totalStorage += pool.getTotal();
                        usedStorage += pool.getUsed();
                    }
                }
                
                // Set CPU resources
                cpu.setTotalCores(totalCores);
                cpu.setTotalThreads(totalCores); // Proxmox reports logical CPUs as cpus
                
                // Get VM resources to calculate allocated resources
                List<VMResources> vmResources = getVMResourcesList();
                
                // Calculate allocated resources
                int allocatedCores = vmResources.stream()
                    .mapToInt(VMResources::getAllocatedCpuCores)
                    .sum();
                cpu.setAllocatedCores(allocatedCores);
                
                // Set memory resources
                memory.setTotalBytes(totalMemory);
                memory.setActualUsedBytes(usedMemory);
                memory.setAllocatedBytes(vmResources.stream()
                    .mapToLong(VMResources::getAllocatedMemoryBytes)
                    .sum());
                
                // Set storage resources
                storage.setTotalBytes(totalStorage);
                storage.setActualUsedBytes(usedStorage);
                storage.setAllocatedBytes(vmResources.stream()
                    .mapToLong(VMResources::getAllocatedStorageBytes)
                    .sum());
                
                resources.setCpu(cpu);
                resources.setMemory(memory);
                resources.setStorage(storage);
                
                // Set node and VM counts
                resources.setTotalNodes(nodes.size());
                resources.setActiveNodes((int) nodes.stream()
                    .filter(n -> true) // Assume all nodes returned by API are active
                    .count());
                resources.setTotalVMs(vmResources.size());
                resources.setRunningVMs((int) vmResources.stream()
                    .filter(vm -> "running".equals(vm.getStatus()))
                    .count());
                
                // Use calculation service to finalize calculations
                calculationService.finalizeClusterCalculations(resources);
                
                return resources;
                
            } catch (Exception e) {
                LOG.error("Failed to get cluster resources", e);
                throw new RuntimeException("Failed to get cluster resources", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NodeResources> getNodeResources(String nodeId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NodeStatus status = nodeService.getNodeStatus(nodeId, null);
                List<VM> nodeVMs = nodeService.getNodeVMs(nodeId, null);
                List<com.coffeesprout.client.StoragePool> storagePools = nodeService.getNodeStorage(nodeId, null);
                
                NodeResources resources = new NodeResources();
                resources.setNodeId(nodeId);
                resources.setNodeName(nodeId);
                resources.setStatus("online"); // Assume online if we can query it
                resources.setTimestamp(Instant.now());
                
                // Set CPU resources
                if (status.getCpuInfo() != null) {
                    NodeResources.NodeCpuResources cpu = new NodeResources.NodeCpuResources();
                    cpu.setPhysicalCores(status.getCpuInfo().getCpus());
                    cpu.setLogicalCores(status.getCpuInfo().getCpus());
                    
                    // Calculate allocated cores
                    int allocatedCores = nodeVMs.stream()
                        .mapToInt(VM::getCpus)
                        .sum();
                    cpu.setAllocatedCores(allocatedCores);
                    
                    resources.setCpu(cpu);
                }
                
                // Set memory resources
                if (status.getMemory() != null) {
                    NodeResources.NodeMemoryResources memory = new NodeResources.NodeMemoryResources();
                    memory.setTotalBytes(status.getMemory().getTotal());
                    memory.setUsedBytes(status.getMemory().getUsed());
                    memory.setFreeBytes(status.getMemory().getFree());
                    
                    // Calculate allocated memory
                    long allocatedMemory = nodeVMs.stream()
                        .mapToLong(VM::getMaxmem)
                        .sum();
                    memory.setAllocatedBytes(allocatedMemory);
                    
                    memory.setUsagePercent((double) status.getMemory().getUsed() / 
                        status.getMemory().getTotal() * 100);
                    
                    resources.setMemory(memory);
                }
                
                // Set storage resources
                NodeResources.NodeStorageResources storage = new NodeResources.NodeStorageResources();
                List<NodeResources.NodeStoragePool> pools = new ArrayList<>();
                
                long totalStorage = 0;
                long usedStorage = 0;
                
                for (com.coffeesprout.client.StoragePool pool : storagePools) {
                    NodeResources.NodeStoragePool nodePool = new NodeResources.NodeStoragePool();
                    nodePool.setPoolId(pool.getStorage());
                    nodePool.setPoolName(pool.getStorage());
                    nodePool.setType(pool.getType());
                    nodePool.setTotalBytes(pool.getTotal());
                    nodePool.setUsedBytes(pool.getUsed());
                    nodePool.setAvailableBytes(pool.getAvail());
                    nodePool.setActive(pool.getActive() == 1);
                    nodePool.setShared(isSharedStorage(pool.getType()));
                    
                    pools.add(nodePool);
                    totalStorage += pool.getTotal();
                    usedStorage += pool.getUsed();
                }
                
                storage.setPools(pools);
                storage.setTotalBytes(totalStorage);
                storage.setUsedBytes(usedStorage);
                storage.setAvailableBytes(totalStorage - usedStorage);
                resources.setStorage(storage);
                
                // Set VM information
                resources.setVmCount(nodeVMs.size());
                resources.setRunningVMs((int) nodeVMs.stream()
                    .filter(vm -> "running".equals(vm.getStatus()))
                    .count());
                resources.setVmIds(nodeVMs.stream()
                    .map(vm -> String.valueOf(vm.getVmid()))
                    .collect(Collectors.toList()));
                
                // Calculate resource pressure
                if (resources.getCpu() != null) {
                    resources.setCpuPressure(calculationService.calculateCpuPressure(
                        resources.getCpu().getAllocatedCores(),
                        resources.getCpu().getPhysicalCores()
                    ));
                }
                
                if (resources.getMemory() != null) {
                    resources.setMemoryPressure(calculationService.calculateMemoryPressure(
                        resources.getMemory().getUsedBytes(),
                        resources.getMemory().getTotalBytes()
                    ));
                }
                
                if (resources.getStorage() != null) {
                    resources.setStoragePressure(calculationService.calculateStoragePressure(
                        resources.getStorage().getUsedBytes(),
                        resources.getStorage().getTotalBytes()
                    ));
                }
                
                return resources;
                
            } catch (Exception e) {
                LOG.error("Failed to get node resources for: " + nodeId, e);
                throw new RuntimeException("Failed to get node resources", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<NodeInfo>> getNodes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Node> nodes = nodeService.listNodes(null);
                List<NodeInfo> nodeInfos = new ArrayList<>();
                
                for (Node node : nodes) {
                    NodeInfo info = new NodeInfo();
                    info.setNodeId(node.getName());
                    info.setNodeName(node.getName());
                    info.setStatus("online"); // Assume nodes returned by API are online
                    
                    // Get detailed node status
                    try {
                        NodeStatus status = nodeService.getNodeStatus(node.getName(), null);
                        if (status != null) {
                            if (status.getCpuInfo() != null) {
                                info.setCpuCores(status.getCpuInfo().getCpus());
                            }
                            if (status.getMemory() != null) {
                                info.setMemoryBytes(status.getMemory().getTotal());
                                info.setMemoryUsagePercent(
                                    (double) status.getMemory().getUsed() / 
                                    status.getMemory().getTotal() * 100
                                );
                            }
                        }
                        
                        // Get VM count
                        List<VM> vms = nodeService.getNodeVMs(node.getName(), null);
                        info.setTotalVMs(vms.size());
                        info.setRunningVMs((int) vms.stream()
                            .filter(vm -> "running".equals(vm.getStatus()))
                            .count());
                        
                    } catch (Exception e) {
                        LOG.warn("Failed to get detailed info for node: " + node.getName(), e);
                    }
                    
                    nodeInfos.add(info);
                }
                
                return nodeInfos;
                
            } catch (Exception e) {
                LOG.error("Failed to get nodes", e);
                throw new RuntimeException("Failed to get nodes", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<VMResources>> getVMResources() {
        return CompletableFuture.supplyAsync(() -> getVMResourcesList());
    }
    
    @Override
    public CompletableFuture<List<com.coffeesprout.federation.StoragePool>> getStoragePools() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Node> nodes = nodeService.listNodes(null);
                Map<String, com.coffeesprout.federation.StoragePool> poolMap = new HashMap<>();
                
                for (Node node : nodes) {
                    List<com.coffeesprout.client.StoragePool> nodePools = 
                        nodeService.getNodeStorage(node.getName(), null);
                    
                    for (com.coffeesprout.client.StoragePool pool : nodePools) {
                        String poolId = pool.getStorage();
                        
                        if (poolMap.containsKey(poolId)) {
                            // Update existing pool (shared storage)
                            com.coffeesprout.federation.StoragePool existing = poolMap.get(poolId);
                            existing.setTotalBytes(pool.getTotal());
                            existing.setUsedBytes(pool.getUsed());
                            existing.setAvailableBytes(pool.getAvail());
                            existing.getAccessibleNodes().add(node.getName());
                        } else {
                            // Create new pool entry
                            com.coffeesprout.federation.StoragePool sp = new com.coffeesprout.federation.StoragePool();
                            sp.setPoolId(poolId);
                            sp.setPoolName(poolId);
                            sp.setType(pool.getType());
                            sp.setTotalBytes(pool.getTotal());
                            sp.setUsedBytes(pool.getUsed());
                            sp.setAvailableBytes(pool.getAvail());
                            sp.setActive(pool.getActive() == 1);
                            sp.setShared(isSharedStorage(pool.getType()));
                            
                            Set<String> accessibleNodes = new HashSet<>();
                            accessibleNodes.add(node.getName());
                            sp.setAccessibleNodes(accessibleNodes);
                            
                            // Set storage class based on type
                            sp.setStorageClass(inferStorageClass(pool.getType(), poolId));
                            
                            poolMap.put(poolId, sp);
                        }
                    }
                }
                
                return new ArrayList<>(poolMap.values());
                
            } catch (Exception e) {
                LOG.error("Failed to get storage pools", e);
                throw new RuntimeException("Failed to get storage pools", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<VMCapacity> calculateLargestPossibleVM(ResourceRequirements requirements) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<NodeResources> nodeResources = new ArrayList<>();
                List<Node> nodes = nodeService.listNodes(null);
                
                for (Node node : nodes) {
                    NodeResources nr = getNodeResources(node.getName()).get();
                    nodeResources.add(nr);
                }
                
                return calculationService.calculateLargestPossibleVM(nodeResources, requirements);
                
            } catch (Exception e) {
                LOG.error("Failed to calculate largest possible VM", e);
                throw new RuntimeException("Failed to calculate largest possible VM", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<PlacementRecommendation>> findOptimalPlacement(
            ResourceRequirements requirements) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Node> nodes = nodeService.listNodes(null);
                PlacementRecommendation best = null;
                List<PlacementRecommendation.AlternativePlacement> alternatives = new ArrayList<>();
                
                for (Node node : nodes) {
                    NodeResources resources = getNodeResources(node.getName()).get();
                    PlacementRecommendation recommendation = evaluateNodeForPlacement(
                        resources, requirements);
                    
                    if (recommendation != null) {
                        if (best == null || recommendation.getPlacementScore() > 
                            best.getPlacementScore()) {
                            // Current best becomes an alternative
                            if (best != null) {
                                PlacementRecommendation.AlternativePlacement alt = 
                                    new PlacementRecommendation.AlternativePlacement();
                                alt.setNodeId(best.getRecommendedNodeId());
                                alt.setNodeName(best.getRecommendedNodeName());
                                alt.setPlacementScore(best.getPlacementScore());
                                alternatives.add(alt);
                            }
                            best = recommendation;
                        } else {
                            // Add as alternative
                            PlacementRecommendation.AlternativePlacement alt = 
                                new PlacementRecommendation.AlternativePlacement();
                            alt.setNodeId(recommendation.getRecommendedNodeId());
                            alt.setNodeName(recommendation.getRecommendedNodeName());
                            alt.setPlacementScore(recommendation.getPlacementScore());
                            alternatives.add(alt);
                        }
                    }
                }
                
                if (best != null) {
                    best.setAlternatives(alternatives);
                    return Optional.of(best);
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                LOG.error("Failed to find optimal placement", e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> getProviderMetrics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                ClusterResources resources = getClusterResources().get();
                
                metrics.put("totalCpuCores", resources.getCpu().getTotalCores());
                metrics.put("allocatedCpuCores", resources.getCpu().getAllocatedCores());
                metrics.put("cpuOvercommitRatio", resources.getCpu().getOvercommitRatio());
                metrics.put("totalMemoryGB", resources.getMemory().getTotalBytes() / 
                    (1024.0 * 1024 * 1024));
                metrics.put("allocatedMemoryGB", resources.getMemory().getAllocatedBytes() / 
                    (1024.0 * 1024 * 1024));
                metrics.put("memoryOvercommitRatio", resources.getMemory().getOvercommitRatio());
                metrics.put("totalStorageGB", resources.getStorage().getTotalBytes() / 
                    (1024.0 * 1024 * 1024));
                metrics.put("usedStorageGB", resources.getStorage().getActualUsedBytes() / 
                    (1024.0 * 1024 * 1024));
                metrics.put("totalNodes", resources.getTotalNodes());
                metrics.put("activeNodes", resources.getActiveNodes());
                metrics.put("totalVMs", resources.getTotalVMs());
                metrics.put("runningVMs", resources.getRunningVMs());
                metrics.put("cpuEfficiency", resources.getCpuEfficiency());
                metrics.put("memoryEfficiency", resources.getMemoryEfficiency());
                metrics.put("storageEfficiency", resources.getStorageEfficiency());
                
            } catch (Exception e) {
                LOG.error("Failed to get provider metrics", e);
                metrics.put("error", e.getMessage());
            }
            
            return metrics;
        });
    }
    
    @Override
    public CompletableFuture<Void> refresh() {
        return CompletableFuture.runAsync(() -> {
            cache.clear();
            lastRefreshTime = System.currentTimeMillis();
            LOG.info("Refreshed Proxmox resource provider cache");
        });
    }
    
    // Helper methods
    
    private List<VMResources> getVMResourcesList() {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            List<VMResources> resources = new ArrayList<>();
            
            for (VMResponse vm : vms) {
                VMResources vmr = new VMResources();
                vmr.setVmId(String.valueOf(vm.vmid()));
                vmr.setVmName(vm.name() != null ? vm.name() : "VM-" + vm.vmid());
                vmr.setNodeName(vm.node());
                vmr.setStatus(vm.status());
                vmr.setAllocatedCpuCores(vm.cpus());
                vmr.setAllocatedMemoryBytes(vm.maxmem());
                vmr.setAllocatedStorageBytes(vm.maxdisk());
                
                // VMResponse doesn't include runtime usage stats
                // Would need to fetch from VM status endpoint for each VM
                vmr.setCpuUsagePercent(0.0);
                vmr.setMemoryUsedBytes(0);
                vmr.setStorageUsedBytes(0);
                
                // Note: VM class doesn't have getTags() method in current implementation
                // Tags would need to be fetched from VM config if needed
                
                resources.add(vmr);
            }
            
            return resources;
            
        } catch (Exception e) {
            LOG.error("Failed to get VM resources", e);
            return new ArrayList<>();
        }
    }
    
    private PlacementRecommendation evaluateNodeForPlacement(NodeResources node, 
                                                            ResourceRequirements requirements) {
        PlacementRecommendation recommendation = new PlacementRecommendation();
        recommendation.setRecommendedNodeId(node.getNodeId());
        recommendation.setRecommendedNodeName(node.getNodeName());
        
        PlacementRecommendation.ResourceFit fit = new PlacementRecommendation.ResourceFit();
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check CPU fit
        boolean cpuFits = true;
        if (requirements.getCpuCores() != null && node.getCpu() != null) {
            int available = node.getCpu().getAvailableCores();
            if (available >= requirements.getCpuCores()) {
                fit.setCpuFitScore(100.0 * (1 - node.getCpuPressure()));
                reasons.add("Sufficient CPU cores available");
            } else {
                fit.setCpuFitScore(0.0);
                cpuFits = false;
            }
        }
        
        // Check memory fit
        boolean memoryFits = true;
        if (requirements.getMemoryBytes() != null && node.getMemory() != null) {
            long available = node.getMemory().getAvailableBytes();
            if (available >= requirements.getMemoryBytes()) {
                fit.setMemoryFitScore(100.0 * (1 - node.getMemoryPressure()));
                reasons.add("Sufficient memory available");
            } else {
                fit.setMemoryFitScore(0.0);
                memoryFits = false;
            }
        }
        
        // Check storage fit
        boolean storageFits = true;
        if (requirements.getStorageBytes() != null && node.getStorage() != null) {
            long available = node.getStorage().getAvailableBytes();
            if (available >= requirements.getStorageBytes()) {
                fit.setStorageFitScore(100.0 * (1 - node.getStoragePressure()));
                reasons.add("Sufficient storage available");
            } else {
                fit.setStorageFitScore(0.0);
                storageFits = false;
            }
        }
        
        fit.setMeetsAllRequirements(cpuFits && memoryFits && storageFits);
        
        // Calculate overall placement score
        double score = (fit.getCpuFitScore() + fit.getMemoryFitScore() + 
                       fit.getStorageFitScore()) / 3.0;
        
        // Apply penalties for high resource pressure
        if (node.getCpuPressure() > 0.8) {
            score *= 0.8;
            warnings.add("High CPU pressure on node");
        }
        if (node.getMemoryPressure() > 0.8) {
            score *= 0.7;
            warnings.add("High memory pressure on node");
        }
        
        recommendation.setPlacementScore(score);
        recommendation.setResourceFit(fit);
        recommendation.setReasons(reasons);
        recommendation.setWarnings(warnings);
        
        return recommendation;
    }
    
    private boolean isSharedStorage(String type) {
        return type != null && (
            type.contains("nfs") || 
            type.contains("ceph") || 
            type.contains("gluster") ||
            type.contains("iscsi")
        );
    }
    
    private String inferStorageClass(String type, String name) {
        if (type == null) return "standard";
        
        String lowerType = type.toLowerCase();
        String lowerName = name.toLowerCase();
        
        if (lowerType.contains("nvme") || lowerName.contains("nvme")) {
            return "NVMe";
        } else if (lowerType.contains("ssd") || lowerName.contains("ssd")) {
            return "SSD";
        } else if (lowerType.contains("hdd") || lowerName.contains("hdd")) {
            return "HDD";
        }
        
        // Default mappings
        switch (lowerType) {
            case "lvm":
            case "lvmthin":
            case "zfs":
            case "zfspool":
                return "SSD"; // Assume local storage is SSD
            case "nfs":
            case "cifs":
            case "glusterfs":
                return "HDD"; // Network storage often HDD
            case "ceph":
            case "rbd":
                return "SSD"; // Ceph typically on SSDs
            default:
                return "standard";
        }
    }
    
    // The helper methods are no longer needed since we're calling services directly
}