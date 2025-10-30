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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v1/backups/health")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Backup Health", description = "Backup health monitoring and compliance endpoints")
public class BackupHealthResource {

    private static final Logger LOG = LoggerFactory.getLogger(BackupHealthResource.class);

    @Inject
    BackupAnalyticsService analyticsService;

    @ConfigProperty(name = "moxxie.backup.health.coverage-threshold-days", defaultValue = "3")
    int defaultCoverageThresholdDays;

    @ConfigProperty(name = "moxxie.backup.health.overdue-threshold-days", defaultValue = "7")
    int defaultOverdueThresholdDays;

    @GET
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get overall backup health",
               description = "Get comprehensive backup health status including coverage and compliance metrics")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backup health calculated successfully",
            content = @Content(schema = @Schema(implementation = BackupHealth.class))),
        @APIResponse(responseCode = "500", description = "Failed to calculate backup health",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getBackupHealth(
            @Parameter(description = "Days to consider backup as recent", required = false)
            @QueryParam("coverageThresholdDays") Integer coverageThresholdDays,
            @Parameter(description = "Days after which backup is overdue", required = false)
            @QueryParam("overdueThresholdDays") Integer overdueThresholdDays) {
        try {
            int coverage = coverageThresholdDays != null ? coverageThresholdDays : defaultCoverageThresholdDays;
            int overdue = overdueThresholdDays != null ? overdueThresholdDays : defaultOverdueThresholdDays;

            BackupHealth health = analyticsService.getBackupHealth(coverage, overdue, null);
            return Response.ok(health).build();
        } catch (Exception e) {
            LOG.error("Failed to calculate backup health", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to calculate backup health: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/coverage")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get backup coverage details",
               description = "Get detailed backup coverage information for all VMs")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backup coverage retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupCoverage[].class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve backup coverage",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getBackupCoverage(
            @Parameter(description = "Only show VMs without recent backups", required = false)
            @QueryParam("overdueOnly") @DefaultValue("false") boolean overdueOnly,
            @Parameter(description = "Days to consider backup as overdue", required = false)
            @QueryParam("thresholdDays") Integer thresholdDays) {
        try {
            int threshold = thresholdDays != null ? thresholdDays : defaultOverdueThresholdDays;

            if (overdueOnly) {
                List<BackupCoverage> overdue = analyticsService.getVMsWithoutRecentBackups(threshold, null);
                return Response.ok(overdue).build();
            } else {
                BackupHealth health = analyticsService.getBackupHealth(threshold, threshold, null);
                return Response.ok(health.coverage()).build();
            }
        } catch (Exception e) {
            LOG.error("Failed to get backup coverage", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get backup coverage: " + e.getMessage()))
                    .build();
        }
    }
}
