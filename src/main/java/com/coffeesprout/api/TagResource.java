package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.util.TagUtils;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/v1/tags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Tags", description = "Tag management endpoints")
public class TagResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(TagResource.class);
    
    @Inject
    TagService tagService;
    
    @Inject
    VMService vmService;
    
    @GET
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get all unique tags", description = "Get all unique tags in use across all VMs")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tags retrieved successfully",
            content = @Content(schema = @Schema(implementation = TagsListResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve tags",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getAllTags() {
        try {
            Set<String> tags = tagService.getAllUniqueTags(null);
            return Response.ok(new TagsListResponse(tags, tags.size())).build();
        } catch (Exception e) {
            LOG.error("Failed to get all tags", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get tags: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/{tag}/vms")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get VMs by tag", description = "Get all VMs that have a specific tag")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VMs retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMResponse[].class))),
        @APIResponse(responseCode = "400", description = "Invalid tag",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VMs",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVMsByTag(
            @Parameter(description = "Tag to search for", required = true, example = "client-nixz")
            @PathParam("tag") String tag) {
        try {
            if (tag == null || tag.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Tag cannot be empty"))
                        .build();
            }
            
            // Get VM IDs with this tag
            List<Integer> vmIds = tagService.getVMsByTag(tag, null);
            
            // Get full VM information
            List<VMResponse> allVMs = vmService.listVMs(null);
            List<VMResponse> taggedVMs = allVMs.stream()
                .filter(vm -> vmIds.contains(vm.vmid()))
                .collect(Collectors.toList());
            
            return Response.ok(taggedVMs).build();
        } catch (Exception e) {
            LOG.error("Failed to get VMs by tag: " + tag, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get VMs: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/bulk")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Bulk tag operations", description = "Add or remove tags from multiple VMs")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Bulk operation completed",
            content = @Content(schema = @Schema(implementation = BulkTagResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to perform bulk operation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response bulkTagOperation(
            @Parameter(description = "List of VM IDs to tag (use either this or namePattern)")
            @QueryParam("vmIds") String vmIds,
            @Parameter(description = "VM name pattern (e.g., nixz-*, *-prod*)")
            @QueryParam("namePattern") String namePattern,
            @RequestBody(description = "Bulk tag operation request", required = true,
                content = @Content(schema = @Schema(implementation = BulkTagRequest.class)))
            @Valid BulkTagRequest request) {
        try {
            // Validate request
            if ((vmIds == null || vmIds.isEmpty()) && (namePattern == null || namePattern.isEmpty())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Either vmIds or namePattern must be provided"))
                        .build();
            }
            
            if (request.tags() == null || request.tags().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Tags list cannot be empty"))
                        .build();
            }
            
            // Validate all tags
            for (String tag : request.tags()) {
                if (!TagUtils.isValidTag(tag)) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("Invalid tag format: " + tag))
                            .build();
                }
            }
            
            // Determine which VMs to update
            List<Integer> targetVmIds = new ArrayList<>();
            
            if (vmIds != null && !vmIds.isEmpty()) {
                // Parse VM IDs from comma-separated string
                for (String id : vmIds.split(",")) {
                    try {
                        targetVmIds.add(Integer.parseInt(id.trim()));
                    } catch (NumberFormatException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(new ErrorResponse("Invalid VM ID: " + id))
                                .build();
                    }
                }
            } else if (namePattern != null && !namePattern.isEmpty()) {
                // Find VMs by name pattern
                targetVmIds = tagService.findVMsByNamePattern(namePattern, null);
                if (targetVmIds.isEmpty()) {
                    return Response.ok(new BulkTagResponse(Map.of(), 
                        "No VMs found matching pattern: " + namePattern)).build();
                }
            }
            
            // Perform the operation
            Map<Integer, String> results;
            Set<String> tagSet = new HashSet<>(request.tags());
            
            if (request.action() == BulkTagRequest.Action.ADD) {
                results = tagService.bulkAddTags(targetVmIds, tagSet, null);
            } else {
                results = tagService.bulkRemoveTags(targetVmIds, tagSet, null);
            }
            
            // Count successes
            long successCount = results.values().stream()
                .filter("success"::equals)
                .count();
            
            String message = String.format("%s %d tags %s %d/%d VMs successfully",
                request.action() == BulkTagRequest.Action.ADD ? "Added" : "Removed",
                request.tags().size(),
                request.action() == BulkTagRequest.Action.ADD ? "to" : "from",
                successCount,
                targetVmIds.size()
            );
            
            return Response.ok(new BulkTagResponse(results, message)).build();
        } catch (Exception e) {
            LOG.error("Failed to perform bulk tag operation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to perform bulk operation: " + e.getMessage()))
                    .build();
        }
    }
    
    // DTOs
    
    @Schema(description = "List of unique tags in use")
    public record TagsListResponse(
        @Schema(description = "All unique tags", example = "[\"moxxie\", \"client-nixz\", \"env-prod\"]")
        Set<String> tags,
        
        @Schema(description = "Total number of unique tags", example = "3")
        int count
    ) {}
    
    @Schema(description = "Bulk tag operation request")
    public record BulkTagRequest(
        @Schema(description = "Action to perform", required = true, example = "ADD")
        @NotNull(message = "Action is required")
        Action action,
        
        @Schema(description = "Tags to add or remove", required = true, 
                example = "[\"client-nixz\", \"env-prod\"]")
        @NotEmpty(message = "Tags list cannot be empty")
        List<String> tags
    ) {
        public enum Action {
            ADD,
            REMOVE
        }
    }
    
    @Schema(description = "Bulk tag operation response")
    public record BulkTagResponse(
        @Schema(description = "Results per VM ID", 
                example = "{\"101\": \"success\", \"102\": \"error: VM not found\"}")
        Map<Integer, String> results,
        
        @Schema(description = "Summary message", 
                example = "Added 2 tags to 3/4 VMs successfully")
        String message
    ) {}
}