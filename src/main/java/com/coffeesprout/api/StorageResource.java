package com.coffeesprout.api;

import java.io.InputStream;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.StorageService;
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

@Path("/api/v1/storage")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Storage", description = "Storage pool and content management endpoints")
public class StorageResource {

    private static final Logger LOG = LoggerFactory.getLogger(StorageResource.class);

    @Inject
    StorageService storageService;

    @GET
    @SafeMode(false)  // Read operation
    @Operation(summary = "List storage pools",
               description = "Get all storage pools with usage statistics across the cluster")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage pools retrieved successfully",
            content = @Content(schema = @Schema(implementation = StoragePoolResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve storage pools",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listStoragePools() {
        try {
            List<StoragePoolResponse> pools = storageService.listStoragePools(null);
            return Response.ok(pools).build();
        } catch (Exception e) {
            LOG.error("Failed to list storage pools", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list storage pools: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{storageId}/content")
    @SafeMode(false)  // Read operation
    @Operation(summary = "List storage content",
               description = "Get content of a specific storage pool with optional type filtering")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage content retrieved successfully",
            content = @Content(schema = @Schema(implementation = StorageContentResponse[].class))),
        @APIResponse(responseCode = "404", description = "Storage not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve storage content",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listStorageContent(
            @Parameter(description = "Storage pool identifier", required = true, example = "local")
            @PathParam("storageId") String storageId,
            @Parameter(description = "Filter by content type (iso, vztmpl, backup, images, rootdir)")
            @QueryParam("type") String contentType) {
        try {
            List<StorageContentResponse> content = storageService.listStorageContent(storageId, contentType, null);
            return Response.ok(content).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to list storage content for: " + storageId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list storage content: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{storageId}/status")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get storage status",
               description = "Get detailed status and usage information for a specific storage on a node")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage status retrieved successfully",
            content = @Content(schema = @Schema(implementation = StorageStatusResponse.class))),
        @APIResponse(responseCode = "404", description = "Storage not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve storage status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getStorageStatus(
            @Parameter(description = "Storage pool identifier", required = true, example = "local")
            @PathParam("storageId") String storageId,
            @Parameter(description = "Node name (required for node-specific storage)", example = "pve1")
            @QueryParam("node") String node) {
        try {
            // If no node specified, try to find one that has this storage
            if (node == null || node.isEmpty()) {
                // Get storage pools to find a suitable node
                List<StoragePoolResponse> pools = storageService.listStoragePools(null);
                var pool = pools.stream()
                        .filter(p -> p.storage().equals(storageId))
                        .findFirst()
                        .orElseThrow(() -> ProxmoxException.notFound("Storage", storageId));

                if (pool.nodes() == null || pool.nodes().isEmpty()) {
                    throw ProxmoxException.invalidConfiguration("storage",
                        "No nodes available for storage: " + storageId,
                        "Ensure the storage pool is configured with at least one node");
                }

                node = pool.nodes().get(0);
            }

            StorageStatusResponse status = storageService.getStorageStatus(node, storageId, null);
            return Response.ok(status).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get storage status for: " + storageId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get storage status: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{storageId}/content/{volumeId}")
    @SafeMode(true)  // Write operation - deleting content
    @Operation(summary = "Delete storage content",
               description = "Delete a specific file from storage (ISOs, templates, etc)")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Content deleted successfully"),
        @APIResponse(responseCode = "403", description = "Cannot delete protected content",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Content not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to delete content",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteStorageContent(
            @Parameter(description = "Storage pool identifier", required = true, example = "local")
            @PathParam("storageId") String storageId,
            @Parameter(description = "Volume identifier", required = true,
                      example = "iso/ubuntu-22.04.3-server.iso")
            @PathParam("volumeId") String volumeId) {
        try {
            // Reconstruct full volume ID
            String fullVolid = storageId + ":" + volumeId;

            storageService.deleteStorageContent(fullVolid, null);
            return Response.noContent().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("protected")) {
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
            LOG.error("Failed to delete content: {}:{}", storageId, volumeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete content: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{storageId}/download-url")
    @SafeMode(true)  // Write operation - downloading new content
    @Operation(summary = "Download from URL",
               description = "Download content from a URL directly to storage with optional checksum verification")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Download started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Storage not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to start download",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response downloadFromUrl(
            @Parameter(description = "Storage pool identifier", required = true, example = "local")
            @PathParam("storageId") String storageId,
            @Parameter(description = "Target node (required)", example = "pve1")
            @QueryParam("node") String node,
            @RequestBody(description = "Download request details", required = true)
            @Valid DownloadUrlRequest request) {
        try {
            if (node == null || node.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Node parameter is required"))
                        .build();
            }

            TaskResponse task = storageService.downloadFromUrl(node, storageId, request, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found") || e.getMessage().contains("not support")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to download from URL", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to download from URL: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{storageId}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @SafeMode(true)  // Write operation - uploading new content
    @Operation(summary = "Upload file",
               description = "Upload a file (ISO, template, etc) to storage")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Upload started",
            content = @Content(schema = @Schema(implementation = UploadResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Storage not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "413", description = "File too large",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to upload file",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response uploadFile(
            @Parameter(description = "Storage pool identifier", required = true, example = "local")
            @PathParam("storageId") String storageId,
            @Parameter(description = "Target node (required)", example = "pve1")
            @QueryParam("node") String node,
            @FormParam("content") String contentType,
            @FormParam("file") InputStream file,
            @FormParam("filename") String filename,
            @FormParam("notes") String notes) {
        try {
            if (node == null || node.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Node parameter is required"))
                        .build();
            }

            if (contentType == null || contentType.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Content type is required"))
                        .build();
            }

            if (file == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("File is required"))
                        .build();
            }

            if (filename == null || filename.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Filename is required"))
                        .build();
            }

            // Validate file extension matches content type
            if (contentType.equals("iso") && !filename.toLowerCase().endsWith(".iso")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("ISO files must have .iso extension"))
                        .build();
            }

            UploadResponse response = storageService.uploadToStorage(node, storageId, contentType, file, filename, null);
            return Response.accepted(response).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found") || e.getMessage().contains("not support")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to upload file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to upload file: " + e.getMessage()))
                    .build();
        }
    }
}
