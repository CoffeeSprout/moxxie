package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.NodeResponse;
import com.coffeesprout.api.dto.NodeResourcesResponse;
import com.coffeesprout.api.dto.NodeStatusResponse;
import com.coffeesprout.client.Node;
import com.coffeesprout.client.NodeStatus;
import com.coffeesprout.client.StoragePool;
import com.coffeesprout.client.VM;
import com.coffeesprout.service.NodeService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
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
import java.util.stream.Collectors;

@Path("/api/v1/nodes")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Nodes", description = "Node management endpoints")
public class NodeResource {

    private static final Logger log = LoggerFactory.getLogger(NodeResource.class);

    @Inject
    NodeService nodeService;

    @GET
    @Operation(summary = "List all nodes", description = "Get a list of all nodes in the Proxmox cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nodes retrieved successfully",
            content = @Content(schema = @Schema(implementation = NodeResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve nodes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listNodes() {
        try {
            List<Node> nodes = nodeService.listNodes(null);
            List<NodeResponse> nodeResponses = nodes.stream()
                .map(node -> new NodeResponse(
                    node.getName(),
                    node.getCpu(),
                    node.getMaxmem(),
                    "online", // Proxmox nodes in the list are typically online
                    0L // uptime would need to be fetched from status endpoint
                ))
                .collect(Collectors.toList());
            
            return Response.ok(nodeResponses).build();
        } catch (Exception e) {
            log.error("Failed to list nodes", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list nodes: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{nodeName}/status")
    @Operation(summary = "Get node status", description = "Get detailed status information for a specific node")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Node status retrieved successfully",
            content = @Content(schema = @Schema(implementation = NodeStatusResponse.class))),
        @APIResponse(responseCode = "404", description = "Node not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve node status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getNodeStatus(
            @Parameter(description = "Name of the node", required = true)
            @PathParam("nodeName") String nodeName) {
        try {
            NodeStatus status = nodeService.getNodeStatus(nodeName, null);
            
            // Calculate CPU usage (this is a simplified calculation)
            double cpuUsage = 0.0; // Would need actual CPU usage from Proxmox API
            
            // Calculate memory usage percentage
            double memoryUsagePercentage = 0.0;
            if (status.getMemory() != null && status.getMemory().getTotal() > 0) {
                memoryUsagePercentage = (double) status.getMemory().getUsed() / status.getMemory().getTotal() * 100;
            }
            
            NodeStatusResponse response = new NodeStatusResponse(
                nodeName,
                cpuUsage,
                status.getMemory() != null ? new NodeStatusResponse.MemoryInfo(
                    status.getMemory().getTotal(),
                    status.getMemory().getUsed(),
                    status.getMemory().getFree(),
                    memoryUsagePercentage
                ) : null,
                status.getCpuInfo() != null ? new NodeStatusResponse.CpuInfo(
                    status.getCpuInfo().getCpus(),
                    status.getCpuInfo().getCores(),
                    status.getCpuInfo().getSockets()
                ) : null,
                new double[]{0.0, 0.0, 0.0} // Would need actual load average from Proxmox API
            );
            
            return Response.ok(response).build();
        } catch (ClientWebApplicationException e) {
            // Handle Proxmox client errors (like hostname lookup failures)
            if (e.getResponse().getStatus() == 500 && 
                e.getMessage() != null && 
                e.getMessage().contains("hostname lookup")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Node not found: " + nodeName))
                        .build();
            }
            log.error("Proxmox client error for node: " + nodeName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node status: " + e.getMessage()))
                    .build();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Node not found: " + nodeName))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to get node status for: " + nodeName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node status: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{nodeName}/resources")
    @Operation(summary = "Get node resources", description = "Get VMs and storage information for a specific node")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Node resources retrieved successfully",
            content = @Content(schema = @Schema(implementation = NodeResourcesResponse.class))),
        @APIResponse(responseCode = "404", description = "Node not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve node resources",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getNodeResources(
            @Parameter(description = "Name of the node", required = true)
            @PathParam("nodeName") String nodeName) {
        try {
            // Get VMs on the node
            List<VM> vms = nodeService.getNodeVMs(nodeName, null);
            List<NodeResourcesResponse.VMSummary> vmSummaries = vms.stream()
                .map(vm -> new NodeResourcesResponse.VMSummary(
                    vm.getVmid(),
                    vm.getName() != null ? vm.getName() : "VM-" + vm.getVmid(),
                    vm.getStatus(),
                    vm.getCpus(),
                    vm.getMaxmem(),
                    vm.getMaxdisk()
                ))
                .collect(Collectors.toList());
            
            // Get storage on the node
            List<StoragePool> storage = nodeService.getNodeStorage(nodeName, null);
            List<NodeResourcesResponse.StorageSummary> storageSummaries = storage.stream()
                .map(pool -> new NodeResourcesResponse.StorageSummary(
                    pool.getStorage(),
                    pool.getType(),
                    pool.getTotal(),
                    pool.getUsed(),
                    pool.getAvail(),
                    pool.getActive() == 1
                ))
                .collect(Collectors.toList());
            
            NodeResourcesResponse response = new NodeResourcesResponse(
                nodeName,
                vmSummaries,
                storageSummaries,
                vmSummaries.size(),
                storageSummaries.size()
            );
            
            return Response.ok(response).build();
        } catch (ClientWebApplicationException e) {
            // Handle Proxmox client errors (like hostname lookup failures)
            if (e.getResponse().getStatus() == 500 && 
                e.getMessage() != null && 
                e.getMessage().contains("hostname lookup")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Node not found: " + nodeName))
                        .build();
            }
            log.error("Proxmox client error for node: " + nodeName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node status: " + e.getMessage()))
                    .build();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Node not found: " + nodeName))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to get node resources for: " + nodeName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get node resources: " + e.getMessage()))
                    .build();
        }
    }
}