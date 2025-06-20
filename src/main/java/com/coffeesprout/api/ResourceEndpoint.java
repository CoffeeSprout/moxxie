package com.coffeesprout.api;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.api.dto.federation.*;
import com.coffeesprout.federation.*;
import com.coffeesprout.federation.providers.ProxmoxResourceProvider;
import com.coffeesprout.service.ResourceCacheService;
import com.coffeesprout.service.ResourceCalculationService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * REST API endpoint for federation-ready resource management
 */
@Path("/api/v1/resources")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Resources", description = "Federation-ready resource management endpoints")
public class ResourceEndpoint {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceEndpoint.class);
    
    @Inject
    ProxmoxResourceProvider resourceProvider;
    
    @Inject
    ResourceCalculationService calculationService;
    
    @Inject
    ResourceCacheService cacheService;
    
    @GET
    @Path("/cluster")
    @Operation(summary = "Get cluster-wide resources", 
               description = "Get comprehensive resource information for the entire cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Cluster resources retrieved successfully",
            content = @Content(schema = @Schema(implementation = FederationClusterResourcesResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve cluster resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getClusterResources(
            @Parameter(description = "Include detailed breakdown by node")
            @QueryParam("includeNodes") @DefaultValue("false") boolean includeNodes,
            @Parameter(description = "Include VM resource usage")
            @QueryParam("includeVMs") @DefaultValue("false") boolean includeVMs,
            @Parameter(description = "Use cached data if available")
            @QueryParam("useCache") @DefaultValue("true") boolean useCache) {
        
        try {
            String cacheKey = "cluster-resources-" + includeNodes + "-" + includeVMs;
            
            FederationClusterResourcesResponse response = useCache 
                ? cacheService.get(cacheKey, () -> buildFederationClusterResourcesResponse(includeNodes, includeVMs))
                : buildFederationClusterResourcesResponse(includeNodes, includeVMs);
            
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to get cluster resources", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get cluster resources: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/nodes")
    @Operation(summary = "Get resources for all nodes", 
               description = "Get resource information for all nodes in the cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Node resources retrieved successfully",
            content = @Content(schema = @Schema(implementation = FederationNodeResourcesResponse[].class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve node resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getNodesResources(
            @Parameter(description = "Filter by node status")
            @QueryParam("status") String status,
            @Parameter(description = "Minimum available CPU cores")
            @QueryParam("minCpuCores") Integer minCpuCores,
            @Parameter(description = "Minimum available memory in GB")
            @QueryParam("minMemoryGB") Long minMemoryGB) {
        
        try {
            List<NodeInfo> nodes = resourceProvider.getNodes().get();
            List<FederationNodeResourcesResponse> responses = new ArrayList<>();
            
            for (NodeInfo node : nodes) {
                // Apply filters
                if (status != null && !status.equals(node.getStatus())) {
                    continue;
                }
                
                NodeResources resources = resourceProvider.getNodeResources(node.getNodeId()).get();
                
                if (minCpuCores != null && resources.getCpu() != null && 
                    resources.getCpu().getAvailableCores() < minCpuCores) {
                    continue;
                }
                
                if (minMemoryGB != null && resources.getMemory() != null) {
                    long availableGB = resources.getMemory().getAvailableBytes() / 
                        (1024L * 1024L * 1024L);
                    if (availableGB < minMemoryGB) {
                        continue;
                    }
                }
                
                responses.add(convertToFederationNodeResourcesResponse(resources));
            }
            
            return Response.ok(responses).build();
        } catch (Exception e) {
            log.error("Failed to get node resources", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node resources: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/nodes/{nodeId}")
    @Operation(summary = "Get resources for a specific node", 
               description = "Get detailed resource information for a specific node")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Node resources retrieved successfully",
            content = @Content(schema = @Schema(implementation = FederationNodeResourcesResponse.class))),
        @APIResponse(responseCode = "404", description = "Node not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve node resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getNodeResources(
            @Parameter(description = "Node ID", required = true)
            @PathParam("nodeId") String nodeId) {
        
        try {
            NodeResources resources = cacheService.get(
                "node-resources-" + nodeId,
                () -> {
                    try {
                        return resourceProvider.getNodeResources(nodeId).get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );
            
            if (resources == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Node not found: " + nodeId))
                        .build();
            }
            
            return Response.ok(convertToFederationNodeResourcesResponse(resources)).build();
        } catch (Exception e) {
            log.error("Failed to get node resources for: " + nodeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node resources: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/capacity/calculate")
    @Operation(summary = "Calculate largest possible VM", 
               description = "Calculate the largest VM that can be created with given requirements")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Capacity calculated successfully",
            content = @Content(schema = @Schema(implementation = FederationVMCapacityResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid requirements",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate capacity",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response calculateCapacity(
            @RequestBody(description = "Resource requirements", required = true)
            @Valid ResourceRequirementsRequest request) {
        
        try {
            ResourceRequirements requirements = convertToResourceRequirements(request);
            VMCapacity capacity = resourceProvider.calculateLargestPossibleVM(requirements).get();
            
            return Response.ok(convertToFederationVMCapacityResponse(capacity)).build();
        } catch (Exception e) {
            log.error("Failed to calculate capacity", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate capacity: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/placement/recommend")
    @Operation(summary = "Get VM placement recommendation", 
               description = "Find the optimal node for VM placement based on requirements")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Placement recommendation provided",
            content = @Content(schema = @Schema(implementation = FederationPlacementRecommendationResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid requirements",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "No suitable placement found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to find placement",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response recommendPlacement(
            @RequestBody(description = "Resource requirements", required = true)
            @Valid ResourceRequirementsRequest request) {
        
        try {
            ResourceRequirements requirements = convertToResourceRequirements(request);
            Optional<PlacementRecommendation> recommendation = 
                resourceProvider.findOptimalPlacement(requirements).get();
            
            if (recommendation.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("No suitable placement found for requirements"))
                        .build();
            }
            
            return Response.ok(convertToFederationPlacementRecommendationResponse(recommendation.get()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to find placement", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to find placement: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/storage")
    @Operation(summary = "Get storage pool information", 
               description = "Get information about all storage pools in the cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage pools retrieved successfully",
            content = @Content(schema = @Schema(implementation = FederationStoragePoolResponse[].class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve storage pools",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getStoragePools(
            @Parameter(description = "Filter by storage type")
            @QueryParam("type") String type,
            @Parameter(description = "Filter by storage class")
            @QueryParam("class") String storageClass,
            @Parameter(description = "Only show active pools")
            @QueryParam("activeOnly") @DefaultValue("true") boolean activeOnly) {
        
        try {
            List<StoragePool> pools = resourceProvider.getStoragePools().get();
            
            // Apply filters
            List<StoragePool> filtered = pools.stream()
                .filter(pool -> type == null || type.equals(pool.getType()))
                .filter(pool -> storageClass == null || storageClass.equals(pool.getStorageClass()))
                .filter(pool -> !activeOnly || pool.isActive())
                .collect(Collectors.toList());
            
            List<FederationStoragePoolResponse> responses = filtered.stream()
                .map(this::convertToFederationStoragePoolResponse)
                .collect(Collectors.toList());
            
            return Response.ok(responses).build();
        } catch (Exception e) {
            log.error("Failed to get storage pools", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get storage pools: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/metrics")
    @Operation(summary = "Get provider metrics", 
               description = "Get detailed metrics and statistics for the resource provider")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Metrics retrieved successfully",
            content = @Content(schema = @Schema(implementation = FederationResourceMetricsResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve metrics",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getMetrics() {
        try {
            Map<String, Object> providerMetrics = resourceProvider.getProviderMetrics().get();
            ResourceCacheService.CacheStatistics cacheStats = cacheService.getStatistics();
            
            FederationResourceMetricsResponse response = new FederationResourceMetricsResponse();
            response.setProviderMetrics(providerMetrics);
            response.setCacheStatistics(Map.of(
                "size", cacheStats.getSize(),
                "hits", cacheStats.getHits(),
                "misses", cacheStats.getMisses(),
                "hitRate", cacheStats.getHitRate(),
                "evictions", cacheStats.getEvictions()
            ));
            
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to get metrics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get metrics: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/cache/invalidate")
    @Operation(summary = "Invalidate resource cache", 
               description = "Invalidate cached resource data")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Cache invalidated successfully"),
        @APIResponse(responseCode = "500", description = "Failed to invalidate cache",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response invalidateCache(
            @Parameter(description = "Specific cache key to invalidate")
            @QueryParam("key") String key,
            @Parameter(description = "Pattern to match keys for invalidation")
            @QueryParam("pattern") String pattern) {
        
        try {
            if (key != null) {
                cacheService.invalidate(key);
            } else if (pattern != null) {
                cacheService.invalidatePattern(pattern);
            } else {
                cacheService.clear();
            }
            
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Failed to invalidate cache", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to invalidate cache: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh resource data", 
               description = "Force refresh of resource data from the provider")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Resources refreshed successfully"),
        @APIResponse(responseCode = "500", description = "Failed to refresh resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response refreshResources() {
        try {
            resourceProvider.refresh().get();
            cacheService.clear();
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Failed to refresh resources", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to refresh resources: " + e.getMessage()))
                    .build();
        }
    }
    
    // Helper methods for conversion
    
    private FederationClusterResourcesResponse buildFederationClusterResourcesResponse(boolean includeNodes, 
                                                                  boolean includeVMs) {
        try {
            ClusterResources resources = resourceProvider.getClusterResources().get();
            FederationClusterResourcesResponse response = new FederationClusterResourcesResponse();
            
            // Basic info
            response.setClusterId(resources.getClusterId());
            response.setClusterName(resources.getClusterName());
            response.setProviderId(resources.getProviderId());
            response.setTimestamp(resources.getTimestamp());
            
            // CPU resources
            if (resources.getCpu() != null) {
                response.setCpu(convertCpuResources(resources.getCpu()));
            }
            
            // Memory resources
            if (resources.getMemory() != null) {
                response.setMemory(convertMemoryResources(resources.getMemory()));
            }
            
            // Storage resources
            if (resources.getStorage() != null) {
                response.setStorage(convertStorageResources(resources.getStorage()));
            }
            
            // Summary
            response.setSummary(Map.of(
                "totalNodes", resources.getTotalNodes(),
                "activeNodes", resources.getActiveNodes(),
                "totalVMs", resources.getTotalVMs(),
                "runningVMs", resources.getRunningVMs(),
                "cpuEfficiency", resources.getCpuEfficiency(),
                "memoryEfficiency", resources.getMemoryEfficiency(),
                "storageEfficiency", resources.getStorageEfficiency()
            ));
            
            // Include node details if requested
            if (includeNodes) {
                List<NodeInfo> nodes = resourceProvider.getNodes().get();
                response.setNodes(nodes.stream()
                    .map(this::convertToNodeSummary)
                    .collect(Collectors.toList()));
            }
            
            // Include VM details if requested
            if (includeVMs) {
                List<VMResources> vms = resourceProvider.getVMResources().get();
                response.setVms(vms.stream()
                    .map(this::convertToVMSummary)
                    .collect(Collectors.toList()));
            }
            
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to build cluster resources response", e);
        }
    }
    
    private Map<String, Object> convertCpuResources(ClusterResources.CpuResources cpu) {
        return Map.of(
            "totalCores", cpu.getTotalCores(),
            "totalThreads", cpu.getTotalThreads(),
            "allocatedCores", cpu.getAllocatedCores(),
            "availableCores", cpu.getAvailableCores(),
            "actualUsagePercent", cpu.getActualUsagePercent(),
            "overcommitRatio", cpu.getOvercommitRatio(),
            "maxOvercommit", cpu.getMaxOvercommit()
        );
    }
    
    private Map<String, Object> convertMemoryResources(ClusterResources.MemoryResources memory) {
        return Map.of(
            "totalGB", memory.getTotalBytes() / (1024.0 * 1024 * 1024),
            "allocatedGB", memory.getAllocatedBytes() / (1024.0 * 1024 * 1024),
            "actualUsedGB", memory.getActualUsedBytes() / (1024.0 * 1024 * 1024),
            "availableGB", memory.getAvailableBytes() / (1024.0 * 1024 * 1024),
            "overcommitRatio", memory.getOvercommitRatio(),
            "maxOvercommit", memory.getMaxOvercommit()
        );
    }
    
    private Map<String, Object> convertStorageResources(ClusterResources.StorageResources storage) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalGB", storage.getTotalBytes() / (1024.0 * 1024 * 1024));
        result.put("allocatedGB", storage.getAllocatedBytes() / (1024.0 * 1024 * 1024));
        result.put("actualUsedGB", storage.getActualUsedBytes() / (1024.0 * 1024 * 1024));
        result.put("availableGB", storage.getAvailableBytes() / (1024.0 * 1024 * 1024));
        result.put("thinProvisioningRatio", storage.getThinProvisioningRatio());
        result.put("totalPools", storage.getTotalPools());
        result.put("activePools", storage.getActivePools());
        return result;
    }
    
    private FederationNodeResourcesResponse convertToFederationNodeResourcesResponse(NodeResources resources) {
        FederationNodeResourcesResponse response = new FederationNodeResourcesResponse();
        response.setNodeId(resources.getNodeId());
        response.setNodeName(resources.getNodeName());
        response.setStatus(resources.getStatus());
        response.setTimestamp(resources.getTimestamp());
        
        // Resource pressure
        response.setResourcePressure(Map.of(
            "cpu", resources.getCpuPressure(),
            "memory", resources.getMemoryPressure(),
            "storage", resources.getStoragePressure()
        ));
        
        // VM info
        response.setVmInfo(Map.of(
            "total", resources.getVmCount(),
            "running", resources.getRunningVMs()
        ));
        
        // CPU
        if (resources.getCpu() != null) {
            response.setCpu(Map.of(
                "physicalCores", resources.getCpu().getPhysicalCores(),
                "allocatedCores", resources.getCpu().getAllocatedCores(),
                "availableCores", resources.getCpu().getAvailableCores(),
                "currentUsagePercent", resources.getCpu().getCurrentUsagePercent()
            ));
        }
        
        // Memory
        if (resources.getMemory() != null) {
            response.setMemory(Map.of(
                "totalGB", resources.getMemory().getTotalBytes() / (1024.0 * 1024 * 1024),
                "allocatedGB", resources.getMemory().getAllocatedBytes() / (1024.0 * 1024 * 1024),
                "usedGB", resources.getMemory().getUsedBytes() / (1024.0 * 1024 * 1024),
                "availableGB", resources.getMemory().getAvailableBytes() / (1024.0 * 1024 * 1024),
                "usagePercent", resources.getMemory().getUsagePercent()
            ));
        }
        
        // Storage
        if (resources.getStorage() != null) {
            response.setStorage(Map.of(
                "totalGB", resources.getStorage().getTotalBytes() / (1024.0 * 1024 * 1024),
                "usedGB", resources.getStorage().getUsedBytes() / (1024.0 * 1024 * 1024),
                "availableGB", resources.getStorage().getAvailableBytes() / (1024.0 * 1024 * 1024),
                "poolCount", resources.getStorage().getPools() != null ? 
                    resources.getStorage().getPools().size() : 0
            ));
        }
        
        return response;
    }
    
    private Map<String, Object> convertToNodeSummary(NodeInfo node) {
        return Map.of(
            "nodeId", node.getNodeId(),
            "nodeName", node.getNodeName(),
            "status", node.getStatus(),
            "cpuCores", node.getCpuCores(),
            "memoryGB", node.getMemoryBytes() / (1024.0 * 1024 * 1024),
            "cpuUsagePercent", node.getCpuUsagePercent(),
            "memoryUsagePercent", node.getMemoryUsagePercent(),
            "vmCount", node.getTotalVMs()
        );
    }
    
    private Map<String, Object> convertToVMSummary(VMResources vm) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("vmId", vm.getVmId());
        summary.put("vmName", vm.getVmName());
        summary.put("nodeName", vm.getNodeName());
        summary.put("status", vm.getStatus());
        summary.put("allocatedCpuCores", vm.getAllocatedCpuCores());
        summary.put("allocatedMemoryGB", vm.getAllocatedMemoryBytes() / (1024.0 * 1024 * 1024));
        summary.put("allocatedStorageGB", vm.getAllocatedStorageBytes() / (1024.0 * 1024 * 1024));
        summary.put("cpuUsagePercent", vm.getCpuUsagePercent());
        if (vm.getTags() != null) {
            summary.put("tags", vm.getTags());
        }
        return summary;
    }
    
    private FederationVMCapacityResponse convertToFederationVMCapacityResponse(VMCapacity capacity) {
        FederationVMCapacityResponse response = new FederationVMCapacityResponse();
        response.setNodeId(capacity.getNodeId());
        response.setNodeName(capacity.getNodeName());
        response.setMaxCpuCores(capacity.getMaxCpuCores());
        response.setMaxMemoryGB(capacity.getMaxMemoryBytes() / (1024.0 * 1024 * 1024));
        response.setMaxStorageGB(capacity.getMaxStorageBytes() / (1024.0 * 1024 * 1024));
        response.setLimitingFactor(capacity.getLimitingFactor());
        response.setWithOvercommit(capacity.isWithOvercommit());
        
        if (capacity.getAlternativeNodes() != null) {
            List<Map<String, Object>> alternatives = new ArrayList<>();
            capacity.getAlternativeNodes().forEach((nodeId, alt) -> {
                alternatives.add(Map.of(
                    "nodeId", alt.getNodeId(),
                    "nodeName", alt.getNodeName(),
                    "maxCpuCores", alt.getMaxCpuCores(),
                    "maxMemoryGB", alt.getMaxMemoryBytes() / (1024.0 * 1024 * 1024),
                    "maxStorageGB", alt.getMaxStorageBytes() / (1024.0 * 1024 * 1024),
                    "score", alt.getScore()
                ));
            });
            response.setAlternatives(alternatives);
        }
        
        return response;
    }
    
    private FederationPlacementRecommendationResponse convertToFederationPlacementRecommendationResponse(
            PlacementRecommendation recommendation) {
        FederationPlacementRecommendationResponse response = new FederationPlacementRecommendationResponse();
        response.setRecommendedNodeId(recommendation.getRecommendedNodeId());
        response.setRecommendedNodeName(recommendation.getRecommendedNodeName());
        response.setPlacementScore(recommendation.getPlacementScore());
        response.setReasons(recommendation.getReasons());
        response.setWarnings(recommendation.getWarnings());
        
        if (recommendation.getResourceFit() != null) {
            response.setResourceFit(Map.of(
                "cpuFitScore", recommendation.getResourceFit().getCpuFitScore(),
                "memoryFitScore", recommendation.getResourceFit().getMemoryFitScore(),
                "storageFitScore", recommendation.getResourceFit().getStorageFitScore(),
                "meetsAllRequirements", recommendation.getResourceFit().isMeetsAllRequirements()
            ));
        }
        
        if (recommendation.getAlternatives() != null) {
            List<Map<String, Object>> alternatives = new ArrayList<>();
            for (PlacementRecommendation.AlternativePlacement alt : recommendation.getAlternatives()) {
                Map<String, Object> altMap = new HashMap<>();
                altMap.put("nodeId", alt.getNodeId());
                altMap.put("nodeName", alt.getNodeName());
                altMap.put("placementScore", alt.getPlacementScore());
                alternatives.add(altMap);
            }
            response.setAlternatives(alternatives);
        }
        
        return response;
    }
    
    private FederationStoragePoolResponse convertToFederationStoragePoolResponse(StoragePool pool) {
        FederationStoragePoolResponse response = new FederationStoragePoolResponse();
        response.setPoolId(pool.getPoolId());
        response.setPoolName(pool.getPoolName());
        response.setType(pool.getType());
        response.setStorageClass(pool.getStorageClass());
        response.setTotalGB(pool.getTotalBytes() / (1024.0 * 1024 * 1024));
        response.setUsedGB(pool.getUsedBytes() / (1024.0 * 1024 * 1024));
        response.setAvailableGB(pool.getAvailableBytes() / (1024.0 * 1024 * 1024));
        response.setActive(pool.isActive());
        response.setShared(pool.isShared());
        response.setAccessibleNodes(pool.getAccessibleNodes());
        
        // Features
        Map<String, Boolean> features = new HashMap<>();
        features.put("thinProvisioning", pool.isSupportsThinProvisioning());
        features.put("snapshots", pool.isSupportsSnapshots());
        features.put("replication", pool.isSupportsReplication());
        features.put("encryption", pool.isSupportsEncryption());
        response.setFeatures(features);
        
        return response;
    }
    
    private ResourceRequirements convertToResourceRequirements(ResourceRequirementsRequest request) {
        ResourceRequirements.Builder builder = new ResourceRequirements.Builder();
        
        if (request.getCpuCores() != null) {
            builder.cpuCores(request.getCpuCores());
        }
        if (request.getMemoryGB() != null) {
            builder.memoryGB(request.getMemoryGB().longValue());
        }
        if (request.getStorageGB() != null) {
            builder.storageGB(request.getStorageGB().longValue());
        }
        if (request.getStorageType() != null) {
            builder.storageType(request.getStorageType());
        }
        if (request.getLocation() != null) {
            builder.location(request.getLocation());
        }
        if (request.getHighAvailability() != null) {
            builder.highAvailability(request.getHighAvailability());
        }
        if (request.getPreferredNodes() != null) {
            builder.preferredNodes(new HashSet<>(request.getPreferredNodes()));
        }
        if (request.getExcludedNodes() != null) {
            builder.excludedNodes(new HashSet<>(request.getExcludedNodes()));
        }
        
        return builder.build();
    }
}