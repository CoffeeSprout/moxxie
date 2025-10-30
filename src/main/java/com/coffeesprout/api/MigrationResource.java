package com.coffeesprout.api;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.client.MigrationPreconditionsResponse;
import com.coffeesprout.service.MigrationService;
import com.coffeesprout.service.SafeMode;
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

@Path("/api/v1/vms/{vmId}/migrate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "VM Migration", description = "VM migration operations")
public class MigrationResource {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationResource.class);

    @Inject
    MigrationService migrationService;

    @POST
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Start VM Migration",
        description = "Start migrating a VM to a different node. Returns immediately with task info. " +
                     "Poll the status endpoint or migration history to track progress.")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Migration started successfully",
            content = @Content(schema = @Schema(implementation = MigrationStartResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request or migration not possible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to start migration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response migrateVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @RequestBody(description = "Migration request", required = true,
                content = @Content(schema = @Schema(implementation = MigrationRequest.class)))
            @Valid MigrationRequest request) {

        LOG.info("Received migration request for VM {} to node {}", vmId, request.targetNode());

        try {
            // Use async migration to avoid HTTP timeouts on long migrations
            MigrationStartResponse response = migrationService.startMigrationAsync(vmId, request, null);
            return Response.status(Response.Status.ACCEPTED).entity(response).build();

        } catch (RuntimeException e) {
            String message = e.getMessage();

            // Provide helpful error responses
            if (message.contains("Set allowOfflineMigration=true")) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(message))
                    .build();
            }

            if (message.contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(message))
                    .build();
            }

            LOG.error("Migration failed for VM {}: {}", vmId, message);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Migration failed: " + message))
                .build();

        } catch (Exception e) {
            LOG.error("Unexpected error during migration of VM {}: {}", vmId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Unexpected error: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/check")
    @SafeMode(false) // Read operation
    @Operation(summary = "Check migration preconditions",
        description = "Check if a VM can be migrated to a target node (mainly for bulk operations)")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Precondition check completed",
            content = @Content(schema = @Schema(implementation = MigrationPreconditionsResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to check preconditions",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response checkMigration(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Target node to check", required = true)
            @QueryParam("target") String targetNode) {

        if (targetNode == null || targetNode.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Target node is required"))
                .build();
        }

        try {
            MigrationPreconditionsResponse response = migrationService.checkMigration(vmId, targetNode, null);
            return Response.ok(response).build();

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
            }

            LOG.error("Failed to check migration preconditions for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to check preconditions: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/history")
    @SafeMode(false) // Read operation
    @Operation(summary = "Get migration history", description = "Get migration history for a VM")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Migration history retrieved",
            content = @Content(schema = @Schema(implementation = MigrationHistoryResponse.class)))
    })
    public Response getMigrationHistory(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {

        try {
            List<MigrationHistoryResponse> history = migrationService.getMigrationHistory(vmId);
            return Response.ok(history).build();

        } catch (Exception e) {
            LOG.error("Failed to get migration history for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get migration history: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/status/{migrationId}")
    @SafeMode(false) // Read operation
    @Operation(summary = "Get migration status",
        description = "Get the current status of a specific migration by its ID")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Migration status retrieved",
            content = @Content(schema = @Schema(implementation = MigrationHistoryResponse.class))),
        @APIResponse(responseCode = "404", description = "Migration not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getMigrationStatus(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Migration ID", required = true)
            @PathParam("migrationId") Long migrationId) {

        try {
            MigrationHistoryResponse status = migrationService.getMigrationStatus(migrationId);

            if (status == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Migration " + migrationId + " not found"))
                    .build();
            }

            // Verify the migration is for the correct VM
            if (status.vmId() != vmId) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Migration " + migrationId + " not found for VM " + vmId))
                    .build();
            }

            return Response.ok(status).build();

        } catch (Exception e) {
            LOG.error("Failed to get migration status {}: {}", migrationId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get migration status: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/sync")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Migrate VM (Synchronous)",
        description = "DEPRECATED: Migrate a VM synchronously. This endpoint waits for completion " +
                     "and can timeout on long migrations. Use the async endpoint (POST without /sync) instead.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Migration completed",
            content = @Content(schema = @Schema(implementation = MigrationResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Migration failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Deprecated
    public Response migrateVMSync(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @RequestBody(description = "Migration request", required = true,
                content = @Content(schema = @Schema(implementation = MigrationRequest.class)))
            @Valid MigrationRequest request) {

        LOG.warn("Using deprecated synchronous migration endpoint for VM {}", vmId);

        try {
            MigrationResponse response = migrationService.migrateVM(vmId, request, null);
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.error("Synchronous migration failed for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Migration failed: " + e.getMessage()))
                .build();
        }
    }
}
