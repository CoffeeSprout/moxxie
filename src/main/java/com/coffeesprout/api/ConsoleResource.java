package com.coffeesprout.api;

import com.coffeesprout.client.*;
import com.coffeesprout.service.ConsoleService;
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

@ApplicationScoped
@Path("/api/v1/vms/{vmId}/console")
@RunOnVirtualThread
@Tag(name = "Console", description = "VM Console Access operations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConsoleResource {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleResource.class);

    @Inject
    ConsoleService consoleService;

    @POST
    @Operation(summary = "Create console access token", 
               description = "Generate a console access token for VNC, SPICE, or terminal connection to a VM")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Console access created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ConsoleResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "VM not found"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid console type or request"
        )
    })
    public Response createConsoleAccess(
            @Parameter(description = "VM ID", required = true) @PathParam("vmId") int vmId,
            @RequestBody(description = "Console request details", required = true) @Valid ConsoleRequest request) {
        
        LOG.info("Creating console access for VM {} with type {}", vmId, request.getType());
        
        try {
            ConsoleResponse response = consoleService.createConsoleAccess(vmId, request, null);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid request for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to create console access for VM {}: {}", vmId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create console access"))
                    .build();
        }
    }

    @GET
    @Path("/websocket")
    @Operation(summary = "Get WebSocket connection details", 
               description = "Get WebSocket URL and headers for establishing a console connection")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "WebSocket details retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ConsoleWebSocketResponse.class))
        ),
        @APIResponse(
            responseCode = "404",
            description = "VM not found"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Missing or invalid ticket"
        )
    })
    public Response getWebSocketDetails(
            @Parameter(description = "VM ID", required = true) @PathParam("vmId") int vmId,
            @Parameter(description = "Console ticket from createConsoleAccess", required = true) @QueryParam("ticket") String consoleTicket) {
        
        if (consoleTicket == null || consoleTicket.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Console ticket is required"))
                    .build();
        }
        
        try {
            ConsoleWebSocketResponse response = consoleService.getWebSocketDetails(vmId, consoleTicket, null);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid request for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to get WebSocket details for VM {}: {}", vmId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get WebSocket details"))
                    .build();
        }
    }

    @GET
    @Path("/spice")
    @Produces("application/x-virt-viewer")
    @Operation(summary = "Get SPICE connection file", 
               description = "Generate a .vv file for connecting to the VM using SPICE protocol")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "SPICE connection file generated successfully",
            content = @Content(mediaType = "application/x-virt-viewer")
        ),
        @APIResponse(
            responseCode = "404",
            description = "VM not found"
        ),
        @APIResponse(
            responseCode = "400",
            description = "Missing or invalid ticket"
        )
    })
    public Response getSpiceConnectionFile(
            @Parameter(description = "VM ID", required = true) @PathParam("vmId") int vmId,
            @Parameter(description = "Console ticket from createConsoleAccess", required = true) @QueryParam("ticket") String consoleTicket) {
        
        if (consoleTicket == null || consoleTicket.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Console ticket is required"))
                    .build();
        }
        
        try {
            SpiceConnectionFile file = consoleService.generateSpiceFile(vmId, consoleTicket, null);
            
            return Response.ok(file.getContent())
                    .type(file.getMimeType())
                    .header("Content-Disposition", "attachment; filename=\"" + file.getFilename() + "\"")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid request for VM {}: {}", vmId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to generate SPICE file for VM {}: {}", vmId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to generate SPICE connection file"))
                    .build();
        }
    }
    
    // Helper class for error responses
    public static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
    }
}