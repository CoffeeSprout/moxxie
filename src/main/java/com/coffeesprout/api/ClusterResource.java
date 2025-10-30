package com.coffeesprout.api;

import java.net.URI;
import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.cluster.*;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.service.ClusterProvisioningService;
import io.smallrye.common.annotation.RunOnVirtualThread;
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

@Path("/api/v1/clusters")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Clusters", description = "Cluster provisioning and management endpoints")
public class ClusterResource {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterResource.class);

    @Inject
    ClusterProvisioningService clusterProvisioningService;

    @Context
    UriInfo uriInfo;

    @POST
    @Path("/provision")
    @Operation(summary = "Provision a new cluster",
               description = "Provisions a complete cluster with multiple nodes based on the provided specification. " +
                           "This is an asynchronous operation that returns immediately with an operation ID for tracking.")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Cluster provisioning started",
            content = @Content(schema = @Schema(implementation = ClusterProvisioningResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid cluster specification",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response provisionCluster(
            @RequestBody(description = "Cluster provisioning specification", required = true,
                content = @Content(schema = @Schema(implementation = ClusterSpec.class)))
            @Valid ClusterSpec spec) {

        LOG.info("=== CLUSTER PROVISION ENDPOINT CALLED ===");
        LOG.info("Received cluster provisioning request for '{}'", spec != null ? spec.name() : "null spec");

        String baseUrl = uriInfo.getBaseUri().toString().replaceAll("/$", "");

        // Call the service synchronously (it will start async provisioning internally)
        ClusterProvisioningResponse response = clusterProvisioningService.provisionCluster(spec, baseUrl)
            .await().indefinitely();

        URI operationUri = uriInfo.getAbsolutePathBuilder()
            .replacePath("/api/v1/clusters/operations/{operationId}")
            .build(response.operationId());

        return Response.status(Response.Status.ACCEPTED)
            .entity(response)
            .location(operationUri)
            .build();
    }

    @GET
    @Path("/operations/{operationId}")
    @Operation(summary = "Get cluster provisioning operation status",
               description = "Returns the current status and progress of a cluster provisioning operation")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Operation status retrieved",
            content = @Content(schema = @Schema(implementation = ClusterProvisioningResponse.class))),
        @APIResponse(responseCode = "404", description = "Operation not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getOperationStatus(
            @Parameter(description = "Operation ID", required = true)
            @PathParam("operationId") String operationId) {

        ClusterProvisioningState state = clusterProvisioningService.getOperationState(operationId);

        if (state == null) {
            throw ProxmoxException.notFound("Operation", operationId);
        }

        String baseUrl = uriInfo.getBaseUri().toString().replaceAll("/$", "");
        ClusterProvisioningResponse response = ClusterProvisioningResponse.fromState(state, baseUrl);

        return Response.ok(response).build();
    }

    @GET
    @Path("/operations")
    @Operation(summary = "List all cluster provisioning operations",
               description = "Returns all cluster provisioning operations including completed, failed, and in-progress")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Operations list retrieved",
            content = @Content(schema = @Schema(implementation = ClusterProvisioningResponse[].class)))
    })
    public Response getAllOperations() {
        Collection<ClusterProvisioningState> operations = clusterProvisioningService.getAllOperations();
        String baseUrl = uriInfo.getBaseUri().toString().replaceAll("/$", "");

        var responses = operations.stream()
            .map(state -> ClusterProvisioningResponse.fromState(state, baseUrl))
            .toList();

        return Response.ok(responses).build();
    }

    @POST
    @Path("/operations/{operationId}/cancel")
    @Operation(summary = "Cancel a cluster provisioning operation",
               description = "Attempts to cancel an in-progress cluster provisioning operation")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Operation cancelled",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @APIResponse(responseCode = "404", description = "Operation not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Operation cannot be cancelled",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response cancelOperation(
            @Parameter(description = "Operation ID", required = true)
            @PathParam("operationId") String operationId) {

        ClusterProvisioningState state = clusterProvisioningService.getOperationState(operationId);

        if (state == null) {
            throw ProxmoxException.notFound("Operation", operationId);
        }

        boolean cancelled = clusterProvisioningService.cancelOperation(operationId);

        if (cancelled) {
            return Response.ok(new MessageResponse("Operation cancelled successfully")).build();
        } else {
            throw ProxmoxException.conflict("Operation", "Cannot be cancelled in current state: " + state.getStatus());
        }
    }

    @Schema(description = "Simple message response")
    public record MessageResponse(
        @Schema(description = "Response message")
        String message
    ) {}
}
