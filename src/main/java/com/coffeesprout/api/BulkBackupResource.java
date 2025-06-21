package com.coffeesprout.api;

import com.coffeesprout.api.dto.BulkBackupRequest;
import com.coffeesprout.api.dto.BulkBackupResponse;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.service.BackupService;
import com.coffeesprout.service.SafeMode;
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

@Path("/api/v1/backups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Bulk Backup", description = "Bulk VM backup management endpoints")
public class BulkBackupResource {
    
    private static final Logger log = LoggerFactory.getLogger(BulkBackupResource.class);
    
    @Inject
    BackupService backupService;
    
    @POST
    @Path("/bulk")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Create backups for multiple VMs", 
               description = "Create backups for multiple VMs based on various selection criteria. " +
                            "Backups are created in parallel with configurable concurrency. " +
                            "Supports different backup modes (snapshot, suspend, stop) and compression formats.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Bulk backup operation completed",
            content = @Content(schema = @Schema(implementation = BulkBackupResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create backups",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response bulkCreateBackups(
            @RequestBody(description = "Bulk backup request", required = true,
                content = @Content(schema = @Schema(implementation = BulkBackupRequest.class)))
            @Valid BulkBackupRequest request) {
        try {
            log.info("Starting bulk backup operation with selectors: {} to storage: {}", 
                    request.vmSelectors(), request.storage());
            
            // Validate storage parameter
            if (request.storage() == null || request.storage().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Storage parameter is required"))
                        .build();
            }
            
            // Perform bulk backup operation
            BulkBackupResponse response = backupService.bulkCreateBackups(request, null);
            
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for bulk backup operation", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid request: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to perform bulk backup operation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create backups: " + e.getMessage()))
                    .build();
        }
    }
}