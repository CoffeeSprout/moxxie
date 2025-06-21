package com.coffeesprout.api;

import com.coffeesprout.api.dto.BulkSnapshotRequest;
import com.coffeesprout.api.dto.BulkSnapshotResponse;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.SnapshotService;
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

@Path("/api/v1/snapshots")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Bulk Snapshots", description = "Bulk snapshot management endpoints")
public class BulkSnapshotResource {
    
    private static final Logger log = LoggerFactory.getLogger(BulkSnapshotResource.class);
    
    @Inject
    SnapshotService snapshotService;
    
    @POST
    @Path("/bulk")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Create snapshots for multiple VMs", 
               description = "Create snapshots for multiple VMs based on various selection criteria. " +
                            "Supports VM IDs, name patterns, and tag expressions. " +
                            "Snapshots can have TTL for automatic cleanup.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Bulk snapshot operation completed",
            content = @Content(schema = @Schema(implementation = BulkSnapshotResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create snapshots",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createBulkSnapshots(
            @RequestBody(description = "Bulk snapshot creation request", required = true,
                content = @Content(schema = @Schema(implementation = BulkSnapshotRequest.class)))
            @Valid BulkSnapshotRequest request) {
        try {
            log.info("Starting bulk snapshot creation with selectors: {}", request.vmSelectors());
            
            // Validate snapshot name pattern
            if (!isValidSnapshotNamePattern(request.snapshotName())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid snapshot name pattern. Use placeholders like {vm}, {date}, {time}"))
                        .build();
            }
            
            // Perform bulk snapshot creation
            BulkSnapshotResponse response = snapshotService.bulkCreateSnapshots(request, null);
            
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for bulk snapshot creation", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid request: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create bulk snapshots", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create snapshots: " + e.getMessage()))
                    .build();
        }
    }
    
    private boolean isValidSnapshotNamePattern(String pattern) {
        // Basic validation - ensure pattern isn't too long after expansion
        // Assuming worst case: {vm} = 20 chars, {date} = 8 chars, {time} = 6 chars
        String expanded = pattern
            .replace("{vm}", "x".repeat(20))
            .replace("{date}", "x".repeat(8))
            .replace("{time}", "x".repeat(6));
        
        return expanded.length() <= 60; // Proxmox snapshot name limit
    }
}