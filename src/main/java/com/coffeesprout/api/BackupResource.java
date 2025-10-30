package com.coffeesprout.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.service.BackupJobService;
import com.coffeesprout.service.BackupLifecycleService;
import com.coffeesprout.service.BackupService;
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

@Path("/api/v1/backups")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Backups", description = "VM backup and restore management endpoints")
public class BackupResource {

    private static final Logger LOG = LoggerFactory.getLogger(BackupResource.class);

    @Inject
    BackupService backupService;

    @Inject
    BackupJobService backupJobService;

    @Inject
    BackupLifecycleService lifecycleService;

    @GET
    @SafeMode(false)  // Read operation
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
            LOG.error("Failed to list all backups", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list backups: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{volid}")
    @SafeMode(true)  // Write operation - deleting backups
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
            LOG.error("Failed to delete backup: {}", volid, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete backup: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/restore")
    @SafeMode(true)  // Write operation - creating new VM
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
            LOG.error("Failed to restore VM from backup", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to restore VM: " + e.getMessage()))
                    .build();
        }
    }

    // Backup Job Management

    @GET
    @Path("/jobs")
    @SafeMode(false)  // Read operation
    @Operation(summary = "List backup jobs",
               description = "Get all configured backup jobs from Proxmox cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backup jobs retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupJobResponse[].class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve backup jobs",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listBackupJobs() {
        try {
            List<BackupJobResponse> jobs = backupJobService.listBackupJobs(null);
            return Response.ok(jobs).build();
        } catch (Exception e) {
            LOG.error("Failed to list backup jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list backup jobs: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/jobs/{jobId}")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get backup job details",
               description = "Get details of a specific backup job")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backup job retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupJobResponse.class))),
        @APIResponse(responseCode = "404", description = "Backup job not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve backup job",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getBackupJob(
            @Parameter(description = "Backup job ID", required = true)
            @PathParam("jobId") String jobId) {
        try {
            BackupJobResponse job = backupJobService.getBackupJob(jobId, null);
            return Response.ok(job).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get backup job: {}", jobId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get backup job: " + e.getMessage()))
                    .build();
        }
    }

    // Backup Lifecycle Management

    @GET
    @Path("/retention-candidates")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get retention candidates",
               description = "List backups eligible for deletion based on retention policy")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Retention candidates retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupDeletionCandidate[].class))),
        @APIResponse(responseCode = "400", description = "Invalid retention policy",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to get retention candidates",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getRetentionCandidates(
            @Parameter(description = "Retention policy (e.g., days:30, count:5, monthly:3)", required = true)
            @QueryParam("retentionPolicy") String retentionPolicy,
            @Parameter(description = "Filter by VM tags (comma-separated)")
            @QueryParam("tags") String tags,
            @Parameter(description = "Filter by VM IDs (comma-separated)")
            @QueryParam("vmIds") String vmIds,
            @Parameter(description = "Include protected backups")
            @QueryParam("includeProtected") @DefaultValue("false") boolean includeProtected) {
        try {
            // Validate retention policy
            if (retentionPolicy == null || !retentionPolicy.matches("(days|count|monthly):[0-9]+")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid retention policy format. Use: days:30, count:5, or monthly:3"))
                        .build();
            }

            // Parse tags and VM IDs
            List<String> tagList = tags != null ? Arrays.asList(tags.split(",")) : null;
            List<Integer> vmIdList = null;
            if (vmIds != null) {
                vmIdList = Arrays.stream(vmIds.split(","))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            }

            List<BackupDeletionCandidate> candidates = lifecycleService.getRetentionCandidates(
                    retentionPolicy, tagList, vmIdList, includeProtected, null);

            return Response.ok(candidates).build();
        } catch (Exception e) {
            LOG.error("Failed to get retention candidates", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get retention candidates: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/cleanup")
    @SafeMode(true)  // Write operation - deleting backups
    @Operation(summary = "Clean up old backups",
               description = "Delete backups based on retention policy. Use dryRun=true to preview.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Cleanup completed or preview generated",
            content = @Content(schema = @Schema(implementation = BackupCleanupResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid cleanup request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to cleanup backups",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response cleanupBackups(
            @RequestBody(description = "Cleanup request details", required = true)
            @Valid BackupCleanupRequest request) {
        try {
            BackupCleanupResponse response = lifecycleService.cleanupBackups(request, null);
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Failed to cleanup backups", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to cleanup backups: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{volid}/protect")
    @SafeMode(true)  // Write operation
    @Operation(summary = "Update backup protection",
               description = "Protect or unprotect a backup from deletion")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Protection status updated successfully"),
        @APIResponse(responseCode = "404", description = "Backup not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to update protection",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateBackupProtection(
            @Parameter(description = "Backup volume ID", required = true)
            @PathParam("volid") String volid,
            @Parameter(description = "Protection status", required = true)
            @QueryParam("protect") boolean protect) {
        try {
            String decodedVolid = volid.replace("%3A", ":").replace("%2F", "/");
            lifecycleService.updateBackupProtection(decodedVolid, protect, null);
            return Response.noContent().build();
        } catch (Exception e) {
            LOG.error("Failed to update backup protection", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to update protection: " + e.getMessage()))
                    .build();
        }
    }
}
