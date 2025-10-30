package com.coffeesprout.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.BulkPowerRequest;
import com.coffeesprout.api.dto.BulkPowerResponse;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.service.PowerService;
import com.coffeesprout.service.SafeMode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v1/vms/power")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Bulk Power", description = "Bulk VM power management endpoints")
public class BulkPowerResource {

    private static final Logger LOG = LoggerFactory.getLogger(BulkPowerResource.class);

    @Inject
    PowerService powerService;

    @POST
    @Path("/bulk")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Perform power operations on multiple VMs",
               description = "Start, stop, shutdown, or reboot multiple VMs based on various selection criteria. " +
                            "Operations are performed in parallel with configurable concurrency. " +
                            "VMs already in the desired state can be skipped automatically.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Bulk power operation completed",
            content = @Content(schema = @Schema(implementation = BulkPowerResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to perform power operations",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response bulkPowerOperation(
            @RequestBody(description = "Bulk power operation request", required = true,
                content = @Content(schema = @Schema(implementation = BulkPowerRequest.class)))
            @Valid BulkPowerRequest request) {
        try {
            LOG.info("Starting bulk {} operation with selectors: {}",
                    request.operation(), request.vmSelectors());

            // Validate operation
            if (!isValidOperation(request.operation())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid power operation: " + request.operation()))
                        .build();
            }

            // Perform bulk power operation
            BulkPowerResponse response = powerService.bulkPowerOperation(request, null);

            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid request for bulk power operation", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid request: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to perform bulk power operation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to perform operation: " + e.getMessage()))
                    .build();
        }
    }

    private boolean isValidOperation(BulkPowerRequest.PowerOperation operation) {
        return operation != null;
    }
}
