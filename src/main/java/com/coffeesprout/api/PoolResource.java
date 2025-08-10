package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.PoolResourceSummary;
import com.coffeesprout.service.PoolService;
import com.coffeesprout.service.SafeMode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/api/v1/pools")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Pools", description = "Resource pool management endpoints")
public class PoolResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(PoolResource.class);
    
    @Inject
    PoolService poolService;
    
    @GET
    @Path("/resources")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get resource summaries for all pools", 
               description = "Returns aggregated resource usage (vCPUs, memory, storage) for all VM pools")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Pool resource summaries retrieved successfully",
            content = @Content(schema = @Schema(implementation = PoolResourceSummary[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve pool resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getAllPoolResources() {
        try {
            List<PoolResourceSummary> summaries = poolService.getPoolResourceSummaries(null);
            return Response.ok(summaries).build();
        } catch (Exception e) {
            LOG.error("Failed to get pool resource summaries", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get pool resource summaries: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/{poolName}/resources")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get resource summary for a specific pool", 
               description = "Returns aggregated resource usage (vCPUs, memory, storage) for a specific VM pool")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Pool resource summary retrieved successfully",
            content = @Content(schema = @Schema(implementation = PoolResourceSummary.class))),
        @APIResponse(responseCode = "404", description = "Pool not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve pool resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getPoolResources(
            @Parameter(description = "Pool name", required = true, example = "nixz")
            @PathParam("poolName") String poolName) {
        try {
            PoolResourceSummary summary = poolService.getPoolResourceSummary(poolName, null);
            return Response.ok(summary).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Pool not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Pool not found: " + poolName))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get pool resource summary for: " + poolName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get pool resource summary: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/resources/export/csv")
    @Produces("text/csv")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Export all pool resources to CSV", 
               description = "Export resource summaries for all pools in CSV format for Excel")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "CSV export generated successfully",
            content = @Content(mediaType = "text/csv")),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to generate CSV export",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response exportAllPoolsCSV() {
        try {
            List<PoolResourceSummary> summaries = poolService.getPoolResourceSummaries(null);
            
            StringBuilder csv = new StringBuilder(2048);
            // Pool summary header
            csv.append("Pool Summary\n")
               .append("Pool Name,Total VMs,Running VMs,Stopped VMs,Total vCPUs,Running vCPUs,Total Memory (GB),Running Memory (GB),Total Storage (TB)\n");
            
            for (PoolResourceSummary summary : summaries) {
                csv.append(String.format("%s,%d,%d,%d,%d,%d,%.1f,%.1f,%.1f\n",
                    summary.poolName(),
                    summary.vmCount(),
                    summary.runningVMs(),
                    summary.stoppedVMs(),
                    summary.totalVcpus(),
                    summary.runningVcpus(),
                    summary.totalMemoryBytes() / (1024.0 * 1024 * 1024),
                    summary.runningMemoryBytes() / (1024.0 * 1024 * 1024),
                    summary.totalStorageBytes() / (1024.0 * 1024 * 1024 * 1024)
                ));
            }
            
            csv.append("\n\nVM Details by Pool\n")
               .append("Pool,VM ID,VM Name,vCPUs,Memory (GB),Storage (GB),Status,Node\n");
            
            for (PoolResourceSummary summary : summaries) {
                for (PoolResourceSummary.VMSummary vm : summary.vms()) {
                    csv.append(String.format("%s,%d,%s,%d,%.1f,%.1f,%s,%s\n",
                        summary.poolName(),
                        vm.vmid(),
                        vm.name(),
                        vm.vcpus(),
                        vm.memoryBytes() / (1024.0 * 1024 * 1024),
                        vm.storageBytes() / (1024.0 * 1024 * 1024),
                        vm.status(),
                        vm.node()
                    ));
                }
            }
            
            return Response.ok(csv.toString())
                    .header("Content-Disposition", "attachment; filename=\"pool-resources-export.csv\"")
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to export pool resources to CSV", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to export pool resources: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/{poolName}/resources/export/csv")
    @Produces("text/csv")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Export specific pool resources to CSV", 
               description = "Export resource summary for a specific pool in CSV format for Excel")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "CSV export generated successfully",
            content = @Content(mediaType = "text/csv")),
        @APIResponse(responseCode = "404", description = "Pool not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to generate CSV export",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response exportPoolCSV(
            @Parameter(description = "Pool name", required = true, example = "nixz")
            @PathParam("poolName") String poolName) {
        try {
            PoolResourceSummary summary = poolService.getPoolResourceSummary(poolName, null);
            
            StringBuilder csv = new StringBuilder(1024);
            // Pool summary
            csv.append(String.format("Pool Summary: %s\n", poolName))
               .append("Metric,Value\n")
               .append(String.format("Total VMs,%d\n", summary.vmCount()))
               .append(String.format("Running VMs,%d\n", summary.runningVMs()))
               .append(String.format("Stopped VMs,%d\n", summary.stoppedVMs()))
               .append(String.format("Total vCPUs,%d\n", summary.totalVcpus()))
               .append(String.format("Running vCPUs,%d\n", summary.runningVcpus()))
               .append(String.format("Total Memory,%s\n", summary.totalMemoryHuman()))
               .append(String.format("Running Memory,%s\n", summary.runningMemoryHuman()))
               .append(String.format("Total Storage,%s\n", summary.totalStorageHuman()));
            
            csv.append("\n\nVM Details\n")
               .append("VM ID,VM Name,vCPUs,Memory (GB),Storage (GB),Status,Node\n");
            
            for (PoolResourceSummary.VMSummary vm : summary.vms()) {
                csv.append(String.format("%d,%s,%d,%.1f,%.1f,%s,%s\n",
                    vm.vmid(),
                    vm.name(),
                    vm.vcpus(),
                    vm.memoryBytes() / (1024.0 * 1024 * 1024),
                    vm.storageBytes() / (1024.0 * 1024 * 1024),
                    vm.status(),
                    vm.node()
                ));
            }
            
            return Response.ok(csv.toString())
                    .header("Content-Disposition", String.format("attachment; filename=\"%s-resources-export.csv\"", poolName))
                    .build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Pool not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Pool not found: " + poolName))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to export pool resources to CSV for: " + poolName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to export pool resources: " + e.getMessage()))
                    .build();
        }
    }
}