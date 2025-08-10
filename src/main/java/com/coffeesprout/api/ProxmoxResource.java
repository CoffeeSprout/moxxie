package com.coffeesprout.api;

import com.coffeesprout.api.dto.ClusterDiscoveryResponse;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.Node;
import com.coffeesprout.client.VM;
import com.coffeesprout.service.AuthService;
import com.coffeesprout.service.ClusterService;
import com.coffeesprout.service.NodeService;
import com.coffeesprout.service.VMService;
import io.smallrye.common.annotation.RunOnVirtualThread;
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

@Path("/api/v1/proxmox")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Proxmox", description = "Proxmox cluster management operations")
@RunOnVirtualThread
public class ProxmoxResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProxmoxResource.class);

    @Inject
    AuthService authService;

    @Inject
    ClusterService clusterService;

    @Inject
    NodeService nodeService;

    @Inject
    VMService vmService;

    // Authentication is now handled automatically by TicketManager

    @GET
    @Path("/discover")
    @Operation(summary = "Discover cluster configuration", description = "Discover Proxmox cluster configuration including nodes, storage, and resources")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Discovery successful",
            content = @Content(schema = @Schema(implementation = ClusterDiscoveryResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials in configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Discovery failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response discoverCluster() {
        try {
            // Ticket will be automatically injected by the service layer
            ClusterDiscoveryResponse config = clusterService.discoverCluster(null);
            return Response.ok(config).build();
        } catch (Exception e) {
            LOG.error("Discovery failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Discovery failed: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/nodes")
    @Operation(summary = "List cluster nodes", description = "Get a list of all nodes in the Proxmox cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nodes retrieved successfully",
            content = @Content(schema = @Schema(implementation = Node[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials in configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve nodes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listNodes() {
        try {
            List<Node> nodes = nodeService.listNodes(null);
            return Response.ok(nodes).build();
        } catch (Exception e) {
            LOG.error("Failed to list nodes", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list nodes: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/vms")
    @Operation(summary = "List virtual machines", description = "Get a list of all virtual machines in the Proxmox cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VMs retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials in configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VMs",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listVMs() {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            return Response.ok(vms).build();
        } catch (Exception e) {
            LOG.error("Failed to list VMs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list VMs: " + e.getMessage()))
                    .build();
        }
    }

    // Authentication is now handled automatically, no need for these DTOs
}