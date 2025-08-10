package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.federation.ClusterResources;
import com.coffeesprout.federation.PlacementRecommendation;
import com.coffeesprout.federation.ResourceRequirements;
import com.coffeesprout.federation.VMCapacity;
import com.coffeesprout.federation.providers.ProxmoxResourceProvider;
import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import com.coffeesprout.service.ResourceCacheService;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Federation API endpoints as specified in issue #53
 * These endpoints expose the data Cafn8 needs for federation decisions
 */
@Path("/api/v1/federation")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Federation", description = "Federation-ready API endpoints for Cafn8 integration")
public class FederationResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(FederationResource.class);
    
    @Inject
    ProxmoxResourceProvider resourceProvider;
    
    @Inject
    ResourceCacheService cacheService;
    
    @Inject
    LocationService locationService;
    
    @GET
    @Path("/capacity")
    @Operation(summary = "Get available capacity", 
               description = "Returns available capacity for VM provisioning in this location")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Capacity information retrieved successfully"),
        @APIResponse(responseCode = "500", description = "Failed to retrieve capacity",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getCapacity() {
        try {
            ClusterResources resources = cacheService.get(
                "federation-capacity",
                () -> {
                    try {
                        return resourceProvider.getClusterResources().get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );
            
            LocationInfo location = locationService.getLocationInfo();
            
            Map<String, Object> response = new HashMap<>();
            
            // Location information
            Map<String, String> locationData = new HashMap<>();
            locationData.put("provider", location.provider());
            locationData.put("region", location.region());
            locationData.put("datacenter", location.datacenter());
            response.put("location", locationData);
            
            // Capacity information
            Map<String, Object> capacity = new HashMap<>();
            
            // CPU capacity
            Map<String, Object> vcpus = new HashMap<>();
            vcpus.put("total", resources.getCpu().getTotalCores());
            vcpus.put("available", Math.round(resources.getCpu().getAvailableCores()));
            vcpus.put("reserved", Math.round(resources.getCpu().getTotalCores() * 0.1)); // 10% reserved
            capacity.put("vcpus", vcpus);
            
            // Memory capacity
            Map<String, Object> memory = new HashMap<>();
            memory.put("total", Math.round(resources.getMemory().getTotalBytes() / (1024.0 * 1024 * 1024)));
            memory.put("available", Math.round(resources.getMemory().getAvailableBytes() / (1024.0 * 1024 * 1024)));
            memory.put("reserved", Math.round(resources.getMemory().getTotalBytes() * 0.15 / (1024.0 * 1024 * 1024))); // 15% reserved
            capacity.put("memory_gb", memory);
            
            // Storage capacity (in GB)
            Map<String, Object> storage = new HashMap<>();
            storage.put("total", Math.round(resources.getStorage().getTotalBytes() / (1024.0 * 1024 * 1024)));
            storage.put("available", Math.round(resources.getStorage().getAvailableBytes() / (1024.0 * 1024 * 1024)));
            storage.put("reserved", Math.round(resources.getStorage().getTotalBytes() * 0.1 / (1024.0 * 1024 * 1024))); // 10% reserved
            capacity.put("storage_gb", storage);
            
            response.put("capacity", capacity);
            
            // Calculate largest possible VM
            ResourceRequirements emptyReq = new ResourceRequirements.Builder().build();
            VMCapacity largestVM = resourceProvider.calculateLargestPossibleVM(emptyReq).get();
            
            Map<String, Object> largestPossible = new HashMap<>();
            largestPossible.put("vcpus", largestVM.getMaxCpuCores());
            largestPossible.put("memory_gb", Math.round(largestVM.getMaxMemoryBytes() / (1024.0 * 1024 * 1024)));
            largestPossible.put("storage_gb", Math.round(largestVM.getMaxStorageBytes() / (1024.0 * 1024 * 1024)));
            response.put("largest_possible_vm", largestPossible);
            
            response.put("timestamp", Instant.now().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to get capacity", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get capacity", e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/utilization")
    @Operation(summary = "Get resource utilization", 
               description = "Returns current resource utilization metrics")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Utilization information retrieved successfully"),
        @APIResponse(responseCode = "500", description = "Failed to retrieve utilization",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getUtilization() {
        try {
            ClusterResources resources = cacheService.get(
                "federation-utilization",
                () -> {
                    try {
                        return resourceProvider.getClusterResources().get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );
            
            LocationInfo location = locationService.getLocationInfo();
            
            Map<String, Object> response = new HashMap<>();
            
            // Location information
            Map<String, String> locationData = new HashMap<>();
            locationData.put("provider", location.provider());
            locationData.put("region", location.region());
            locationData.put("datacenter", location.datacenter());
            response.put("location", locationData);
            
            // Utilization percentages
            Map<String, Object> utilization = new HashMap<>();
            utilization.put("vcpu_percent", resources.getCpu().getActualUsagePercent());
            utilization.put("memory_percent", 
                (resources.getMemory().getActualUsedBytes() / (double) resources.getMemory().getTotalBytes()) * 100);
            utilization.put("storage_percent",
                (resources.getStorage().getActualUsedBytes() / (double) resources.getStorage().getTotalBytes()) * 100);
            response.put("utilization", utilization);
            
            // VM counts
            Map<String, Object> vmCount = new HashMap<>();
            vmCount.put("total", resources.getTotalVMs());
            vmCount.put("running", resources.getRunningVMs());
            vmCount.put("stopped", resources.getTotalVMs() - resources.getRunningVMs());
            response.put("vm_count", vmCount);
            
            // Trends (placeholder - would need historical data)
            Map<String, String> trends = new HashMap<>();
            trends.put("vcpu_trend_1h", "+0.0%");
            trends.put("memory_trend_1h", "+0.0%");
            response.put("trends", trends);
            
            response.put("timestamp", Instant.now().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to get utilization", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get utilization", e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/capabilities")
    @Operation(summary = "Get location capabilities", 
               description = "Returns capabilities and constraints of this location")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Capabilities retrieved successfully"),
        @APIResponse(responseCode = "500", description = "Failed to retrieve capabilities",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getCapabilities() {
        try {
            LocationInfo location = locationService.getLocationInfo();
            ClusterResources resources = resourceProvider.getClusterResources().get();
            
            Map<String, Object> response = new HashMap<>();
            
            // Location information
            Map<String, String> locationData = new HashMap<>();
            locationData.put("provider", location.provider());
            locationData.put("region", location.region());
            locationData.put("datacenter", location.datacenter());
            response.put("location", locationData);
            
            // Capabilities
            Map<String, Object> capabilities = new HashMap<>();
            
            // VM types supported (based on Proxmox capabilities)
            capabilities.put("vm_types", new String[]{"general", "compute", "memory"});
            
            // Maximum resources per VM
            capabilities.put("max_vcpus_per_vm", 128);
            capabilities.put("max_memory_gb_per_vm", 512);
            capabilities.put("max_storage_gb_per_vm", 10000);
            
            // Features
            capabilities.put("features", new String[]{
                "snapshots",
                "live_migration",
                "nested_virtualization",
                "cloud_init",
                "qemu_agent",
                "vnc_console",
                "spice_console"
            });
            
            // Networking capabilities
            Map<String, Boolean> networking = new HashMap<>();
            networking.put("ipv6_support", true);
            networking.put("private_networks", true);
            networking.put("floating_ips", false); // Proxmox doesn't have floating IPs
            capabilities.put("networking", networking);
            
            response.put("capabilities", capabilities);
            
            // Constraints (placeholder - would be configured)
            Map<String, Object> constraints = new HashMap<>();
            constraints.put("compliance", new String[]{"gdpr"});
            constraints.put("certifications", new String[]{});
            response.put("constraints", constraints);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to get capabilities", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get capabilities", e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/estimate")
    @Operation(summary = "Estimate cost and feasibility", 
               description = "Estimates cost and checks feasibility for VM provisioning")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Estimate calculated successfully"),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate estimate",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response estimate(
            @RequestBody(description = "Resource requirements for estimation", required = true)
            @Valid EstimateRequest request) {
        
        try {
            // Build resource requirements
            ResourceRequirements.Builder reqBuilder = new ResourceRequirements.Builder();
            if (request.getVcpus() != null) {
                reqBuilder.cpuCores(request.getVcpus());
            }
            if (request.getMemoryGb() != null) {
                reqBuilder.memoryGB(request.getMemoryGb().longValue());
            }
            if (request.getStorageGb() != null) {
                reqBuilder.storageGB(request.getStorageGb().longValue());
            }
            
            ResourceRequirements requirements = reqBuilder.build();
            
            // Check if placement is possible
            Optional<PlacementRecommendation> placement = 
                resourceProvider.findOptimalPlacement(requirements).get();
            
            Map<String, Object> response = new HashMap<>();
            
            response.put("feasible", placement.isPresent());
            
            // Cost estimation (placeholder - Proxmox doesn't have built-in pricing)
            Map<String, Object> estimatedCost = new HashMap<>();
            if (placement.isPresent()) {
                // Simple cost calculation based on resources
                double monthlyCost = 0.0;
                if (request.getVcpus() != null) {
                    monthlyCost += request.getVcpus() * 10.0; // $10 per vCPU
                }
                if (request.getMemoryGb() != null) {
                    monthlyCost += request.getMemoryGb() * 5.0; // $5 per GB RAM
                }
                if (request.getStorageGb() != null) {
                    monthlyCost += request.getStorageGb() * 0.1; // $0.10 per GB storage
                }
                
                estimatedCost.put("amount", monthlyCost);
                estimatedCost.put("currency", "USD");
                estimatedCost.put("period", "monthly");
            } else {
                estimatedCost.put("amount", 0.0);
                estimatedCost.put("currency", "USD");
                estimatedCost.put("period", "monthly");
            }
            response.put("estimated_cost", estimatedCost);
            
            // Availability
            Map<String, Object> availability = new HashMap<>();
            availability.put("immediate", placement.isPresent());
            availability.put("wait_time_minutes", placement.isPresent() ? 0 : -1);
            response.put("availability", availability);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to calculate estimate", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate estimate", e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Request DTO for estimate endpoint
     */
    public static class EstimateRequest {
        @JsonProperty("vcpus")
        private Integer vcpus;
        
        @JsonProperty("memory_gb")
        private Double memoryGb;
        
        @JsonProperty("storage_gb")
        private Double storageGb;
        
        @JsonProperty("duration_hours")
        private Integer durationHours;
        
        public Integer getVcpus() {
            return vcpus;
        }
        
        public void setVcpus(Integer vcpus) {
            this.vcpus = vcpus;
        }
        
        public Double getMemoryGb() {
            return memoryGb;
        }
        
        public void setMemoryGb(Double memoryGb) {
            this.memoryGb = memoryGb;
        }
        
        public Double getStorageGb() {
            return storageGb;
        }
        
        public void setStorageGb(Double storageGb) {
            this.storageGb = storageGb;
        }
        
        public Integer getDurationHours() {
            return durationHours;
        }
        
        public void setDurationHours(Integer durationHours) {
            this.durationHours = durationHours;
        }
    }
}