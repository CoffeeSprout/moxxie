package com.coffeesprout.api;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.service.BackupAnalyticsService;
import com.coffeesprout.service.SafeMode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v1/backups/analytics")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Backup Analytics", description = "Backup storage analytics and reporting endpoints")
public class BackupAnalyticsResource {

    private static final Logger LOG = LoggerFactory.getLogger(BackupAnalyticsResource.class);

    @Inject
    BackupAnalyticsService analyticsService;

    @GET
    @Path("/vms")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get storage usage per VM",
               description = "Calculate backup storage usage for each VM with breakdown by storage location")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VM storage usage calculated successfully",
            content = @Content(schema = @Schema(implementation = VMStorageUsage[].class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate storage usage",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVMStorageUsage() {
        try {
            List<VMStorageUsage> usage = analyticsService.getVMStorageUsage(null);
            return Response.ok(usage).build();
        } catch (Exception e) {
            LOG.error("Failed to calculate VM storage usage", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate VM storage usage: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/clients")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get storage usage per client",
               description = "Calculate backup storage usage grouped by client tags")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Client storage usage calculated successfully",
            content = @Content(schema = @Schema(implementation = ClientStorageUsage[].class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate storage usage",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getClientStorageUsage() {
        try {
            List<ClientStorageUsage> usage = analyticsService.getClientStorageUsage(null);
            return Response.ok(usage).build();
        } catch (Exception e) {
            LOG.error("Failed to calculate client storage usage", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate client storage usage: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/storage")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get usage per storage location",
               description = "Calculate backup storage usage for each storage location")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage location usage calculated successfully",
            content = @Content(schema = @Schema(implementation = StorageLocationUsage[].class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate storage usage",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getStorageLocationUsage() {
        try {
            List<StorageLocationUsage> usage = analyticsService.getStorageLocationUsage(null);
            return Response.ok(usage).build();
        } catch (Exception e) {
            LOG.error("Failed to calculate storage location usage", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate storage location usage: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/trends")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get storage growth trends",
               description = "Analyze backup storage growth over time")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Storage trends calculated successfully",
            content = @Content(schema = @Schema(implementation = StorageTrend.class))),
        @APIResponse(responseCode = "400", description = "Invalid period parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate trends",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getStorageTrends(
            @Parameter(description = "Time period for trend analysis",
                      required = false,
                      example = "daily",
                      schema = @Schema(enumeration = {"daily", "weekly", "monthly"}))
            @QueryParam("period") @DefaultValue("daily") String period) {
        try {
            // Validate period
            if (!period.matches("daily|weekly|monthly")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid period. Must be one of: daily, weekly, monthly"))
                        .build();
            }

            StorageTrend trends = analyticsService.getStorageTrends(period, null);
            return Response.ok(trends).build();
        } catch (Exception e) {
            LOG.error("Failed to calculate storage trends", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate storage trends: " + e.getMessage()))
                    .build();
        }
    }
}
