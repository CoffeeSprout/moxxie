package com.coffeesprout.federation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract interface for resource providers (Proxmox, VMware, AWS, etc.)
 * This allows for federation across different virtualization platforms
 */
public interface ResourceProvider {
    
    /**
     * Get unique identifier for this provider
     */
    String getProviderId();
    
    /**
     * Get display name for this provider
     */
    String getProviderName();
    
    /**
     * Get provider type (e.g., "proxmox", "vmware", "aws")
     */
    String getProviderType();
    
    /**
     * Get location/region of this provider
     */
    String getLocation();
    
    /**
     * Check if provider is currently available
     */
    CompletableFuture<Boolean> isAvailable();
    
    /**
     * Get cluster-wide resource summary
     */
    CompletableFuture<ClusterResources> getClusterResources();
    
    /**
     * Get resources for a specific node
     */
    CompletableFuture<NodeResources> getNodeResources(String nodeId);
    
    /**
     * Get all nodes in this provider
     */
    CompletableFuture<List<NodeInfo>> getNodes();
    
    /**
     * Get resources for all VMs
     */
    CompletableFuture<List<VMResources>> getVMResources();
    
    /**
     * Get storage pool information
     */
    CompletableFuture<List<StoragePool>> getStoragePools();
    
    /**
     * Calculate largest possible VM that can be created
     */
    CompletableFuture<VMCapacity> calculateLargestPossibleVM(ResourceRequirements requirements);
    
    /**
     * Find optimal node for VM placement
     */
    CompletableFuture<Optional<PlacementRecommendation>> findOptimalPlacement(ResourceRequirements requirements);
    
    /**
     * Get provider-specific metrics
     */
    CompletableFuture<Map<String, Object>> getProviderMetrics();
    
    /**
     * Get cost estimation for resources (if supported)
     */
    default CompletableFuture<Optional<CostEstimate>> estimateCost(ResourceRequirements requirements) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
    
    /**
     * Refresh cached data
     */
    CompletableFuture<Void> refresh();
}