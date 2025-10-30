package com.coffeesprout.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.NodeDrainRequest;
import com.coffeesprout.api.dto.NodeDrainResponse;
import com.coffeesprout.api.dto.NodeMaintenanceResponse;
import com.coffeesprout.service.NodeMaintenanceService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/v1/nodes")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Node Maintenance", description = "Node maintenance and VM migration workflows")
@RunOnVirtualThread
public class NodeMaintenanceResource {

    private static final Logger LOG = Logger.getLogger(NodeMaintenanceResource.class);

    @Inject
    NodeMaintenanceService nodeMaintenanceService;

    @POST
    @Path("/{node}/drain")
    @Operation(
        summary = "Drain node by migrating all VMs",
        description = "Migrate all VMs off the specified node in preparation for maintenance. " +
                     "VMs are migrated to the best available target nodes based on available resources. " +
                     "Returns immediately with a drain operation ID for progress tracking."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "202",
            description = "Drain operation started",
            content = @Content(schema = @Schema(implementation = NodeDrainResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "409", description = "Node already in maintenance or drain in progress"),
        @APIResponse(responseCode = "500", description = "Drain operation failed")
    })
    public Response drainNode(
        @Parameter(description = "Node name to drain", example = "hv7")
        @PathParam("node") String node,

        @Valid NodeDrainRequest request
    ) {
        LOG.infof("Draining node: %s", node);

        try {
            NodeDrainResponse response = nodeMaintenanceService.drainNode(node, request, null);
            return Response.accepted(response).build();
        } catch (IllegalStateException e) {
            LOG.errorf("Cannot drain node %s: %s", node, e.getMessage());
            return Response.status(Response.Status.CONFLICT)
                          .entity("{\"error\": \"" + e.getMessage() + "\"}")
                          .build();
        }
    }

    @POST
    @Path("/{node}/maintenance")
    @Operation(
        summary = "Enable maintenance mode for node",
        description = "Mark a node as in maintenance mode. If VMs are still running on the node, " +
                     "they can optionally be drained first. While in maintenance mode, the node is " +
                     "excluded from VM placement decisions."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Maintenance mode enabled",
            content = @Content(schema = @Schema(implementation = NodeMaintenanceResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "409", description = "Node already in maintenance")
    })
    public Response enableMaintenance(
        @Parameter(description = "Node name", example = "hv7")
        @PathParam("node") String node,

        @Parameter(description = "Drain VMs before entering maintenance", example = "true")
        @QueryParam("drain") @DefaultValue("false") boolean drain,

        @Parameter(description = "Reason for maintenance", example = "Security updates")
        @QueryParam("reason") String reason
    ) {
        LOG.infof("Enabling maintenance mode for node: %s (drain=%s, reason=%s)", node, drain, reason);

        try {
            NodeMaintenanceResponse response = nodeMaintenanceService.enableMaintenance(node, drain, reason, null);
            return Response.ok(response).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                          .entity("{\"error\": \"" + e.getMessage() + "\"}")
                          .build();
        }
    }

    @DELETE
    @Path("/{node}/maintenance")
    @Operation(
        summary = "Disable maintenance mode for node",
        description = "Remove maintenance mode from a node, making it available for VM placement again. " +
                     "Optionally migrate VMs back to the node (undrain)."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Maintenance mode disabled",
            content = @Content(schema = @Schema(implementation = NodeMaintenanceResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Node not found"),
        @APIResponse(responseCode = "404", description = "Node not in maintenance")
    })
    public Response disableMaintenance(
        @Parameter(description = "Node name", example = "hv7")
        @PathParam("node") String node,

        @Parameter(description = "Migrate VMs back to this node", example = "false")
        @QueryParam("undrain") @DefaultValue("false") boolean undrain
    ) {
        LOG.infof("Disabling maintenance mode for node: %s (undrain=%s)", node, undrain);

        try {
            NodeMaintenanceResponse response = nodeMaintenanceService.disableMaintenance(node, undrain, null);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                          .entity("{\"error\": \"" + e.getMessage() + "\"}")
                          .build();
        }
    }

    @GET
    @Path("/{node}/maintenance")
    @Operation(
        summary = "Get node maintenance status",
        description = "Get the current maintenance status and history for a node"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Maintenance status",
            content = @Content(schema = @Schema(implementation = NodeMaintenanceResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Node not found or no maintenance records")
    })
    public Response getMaintenanceStatus(
        @Parameter(description = "Node name", example = "hv7")
        @PathParam("node") String node
    ) {
        return nodeMaintenanceService.getMaintenanceStatus(node, null)
            .map(status -> Response.ok(status).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{node}/drain/{drainId}/status")
    @Operation(
        summary = "Get drain operation status",
        description = "Get the current status and progress of a drain operation"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Drain status",
            content = @Content(schema = @Schema(implementation = NodeDrainResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Drain operation not found")
    })
    public Response getDrainStatus(
        @Parameter(description = "Node name", example = "hv7")
        @PathParam("node") String node,

        @Parameter(description = "Drain operation ID")
        @PathParam("drainId") String drainId
    ) {
        return nodeMaintenanceService.getDrainStatus(node, drainId)
            .map(status -> Response.ok(status).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{node}/undrain")
    @Operation(
        summary = "Undrain node by migrating VMs back",
        description = "Migrate VMs back to a node after maintenance is complete. " +
                     "Only migrates VMs that were originally on this node before it was drained."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "202",
            description = "Undrain operation started",
            content = @Content(schema = @Schema(implementation = NodeDrainResponse.class))
        ),
        @APIResponse(responseCode = "404", description = "Node not found or no drain history"),
        @APIResponse(responseCode = "409", description = "Node still in maintenance mode")
    })
    public Response undrainNode(
        @Parameter(description = "Node name to undrain", example = "hv7")
        @PathParam("node") String node
    ) {
        LOG.infof("Undraining node: %s", node);

        try {
            NodeDrainResponse response = nodeMaintenanceService.undrainNode(node, null);
            return Response.accepted(response).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                          .entity("{\"error\": \"" + e.getMessage() + "\"}")
                          .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                          .entity("{\"error\": \"" + e.getMessage() + "\"}")
                          .build();
        }
    }
}
