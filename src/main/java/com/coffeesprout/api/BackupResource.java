package com.coffeesprout.api;

import com.coffeesprout.api.dto.BackupResponse;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.RestoreRequest;
import com.coffeesprout.api.dto.TaskResponse;
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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/api/v1/backups")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Backups", description = "VM backup and restore management endpoints")
public class BackupResource {
    
    private static final Logger log = LoggerFactory.getLogger(BackupResource.class);
    
    @Inject
    BackupService backupService;
    
    @GET
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "List all backups", 
               description = "Get all VM backups across all storage locations and nodes")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backups retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupResponse[].class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve backups",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listAllBackups() {
        try {
            List<BackupResponse> backups = backupService.listAllBackups(null);
            return Response.ok(backups).build();
        } catch (Exception e) {
            log.error("Failed to list all backups", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list backups: " + e.getMessage()))
                    .build();
        }
    }
    
    @DELETE
    @Path("/{volid}")
    @SafeMode(value = true)  // Write operation - deleting backups
    @Operation(summary = "Delete a backup", 
               description = "Delete a specific backup by volume ID. Protected backups cannot be deleted.")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Backup deletion started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "403", description = "Cannot delete protected backup",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Backup not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to delete backup",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteBackup(
            @Parameter(description = "Backup volume ID", required = true, 
                      example = "local:backup/vzdump-qemu-100-2024_01_15-10_30_00.vma.zst")
            @PathParam("volid") String volid) {
        try {
            // URL decode the volid (in case it was encoded in the path)
            String decodedVolid = volid.replace("%3A", ":").replace("%2F", "/");
            
            TaskResponse task = backupService.deleteBackup(decodedVolid, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("protected backup")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            } else if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete backup: {}", volid, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete backup: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/restore")
    @SafeMode(value = true)  // Write operation - creating new VM
    @Operation(summary = "Restore VM from backup", 
               description = "Restore a VM from a backup to the same or different node")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM restore started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid restore parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "VM ID already exists (use overwriteExisting=true)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to restore VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response restoreVM(
            @RequestBody(description = "Restore request details", required = true)
            @Valid RestoreRequest request) {
        try {
            TaskResponse task = backupService.restoreBackup(request, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to restore VM from backup", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to restore VM: " + e.getMessage()))
                    .build();
        }
    }
}