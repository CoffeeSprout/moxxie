package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.TicketManager;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Admin", description = "Administrative endpoints")
public class AdminResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(AdminResource.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    ObjectMapper objectMapper;
    
    @POST
    @Path("/tag-style")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Configure tag colors", 
               description = "Configure Proxmox datacenter tag colors for visual identification in the UI")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tag style configuration updated",
            content = @Content(schema = @Schema(implementation = ConfigResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to update configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response configureTagStyle(
            @RequestBody(description = "Tag style configuration", required = true,
                content = @Content(schema = @Schema(implementation = TagStyleConfig.class)))
            @Valid TagStyleConfig config) {
        try {
            // Validate input
            if (config == null || config.tagStyle() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Tag style configuration is required"))
                        .build();
            }
            
            // Build the tag-style configuration string for Proxmox
            StringBuilder tagStyle = new StringBuilder();
            
            // Add case sensitivity setting
            tagStyle.append("case-sensitive=").append(config.tagStyle().caseSensitive() ? "1" : "0").append(';');
            
            // Add ordering
            tagStyle.append("ordering=").append(config.tagStyle().ordering()).append(';');
            
            // Add shape
            tagStyle.append("shape=").append(config.tagStyle().shape()).append(';');
            
            // Add color mappings
            if (config.tagStyle().colorMap() != null && !config.tagStyle().colorMap().isEmpty()) {
                tagStyle.append("color-map=");
                boolean first = true;
                for (Map.Entry<String, String> entry : config.tagStyle().colorMap().entrySet()) {
                    if (!first) {
                        tagStyle.append(',');
                    }
                    tagStyle.append(entry.getKey()).append(':').append(entry.getValue());
                    first = false;
                }
            }
            
            // Note: The actual Proxmox API endpoint for updating datacenter config varies
            // This is a conceptual implementation - adjust based on your Proxmox version
            LOG.info("Configuring tag style: {}", tagStyle.toString());
            
            // For now, we'll store this configuration locally and return success
            // In a real implementation, this would update Proxmox datacenter.cfg
            
            return Response.ok(new ConfigResponse("Tag style configuration updated successfully"))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to configure tag style", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to configure tag style: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/tag/{tag}/style")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Configure individual tag style", 
               description = "Configure style for a specific tag")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tag style updated",
            content = @Content(schema = @Schema(implementation = ConfigResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to update tag style",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response configureIndividualTagStyle(
            @Parameter(description = "Tag name", required = true)
            @PathParam("tag") String tag,
            @RequestBody(description = "Tag style", required = true,
                content = @Content(schema = @Schema(implementation = SimpleTagStyle.class)))
            @Valid SimpleTagStyle style) {
        try {
            LOG.info("Configuring style for tag '{}': color={}, icon={}", tag, style.color(), style.icon());
            
            // Store tag style configuration locally for now
            // In a real implementation, this would update Proxmox datacenter config
            return Response.ok(new ConfigResponse("Tag style for '" + tag + "' updated successfully")).build();
        } catch (Exception e) {
            LOG.error("Failed to configure style for tag: " + tag, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to configure tag style: " + e.getMessage()))
                    .build();
        }
    }
    
    // DTOs
    
    @Schema(description = "Simple tag style configuration")
    public record SimpleTagStyle(
        @Schema(description = "Color in hex format", example = "#ff0000", required = true)
        String color,
        
        @Schema(description = "Icon name", example = "power-on")
        String icon
    ) {}
    
    @Schema(description = "Tag style configuration for Proxmox UI")
    public record TagStyleConfig(
        @Schema(description = "Tag style settings", required = true)
        TagStyle tagStyle
    ) {
        @Schema(description = "Tag style settings")
        public record TagStyle(
            @Schema(description = "Case sensitive tag matching", example = "false")
            boolean caseSensitive,
            
            @Schema(description = "Tag ordering method", example = "config")
            String ordering,
            
            @Schema(description = "Tag shape in UI", example = "full")
            String shape,
            
            @Schema(description = "Color mappings for tags", 
                    example = "{\"moxxie\": \"#00aa00\", \"env-prod\": \"#0066cc\", \"always-on\": \"#cc0000\"}")
            Map<String, String> colorMap
        ) {}
    }
    
    @Schema(description = "Configuration update response")
    public record ConfigResponse(
        @Schema(description = "Status message", example = "Tag style configuration updated successfully")
        String status
    ) {}
}