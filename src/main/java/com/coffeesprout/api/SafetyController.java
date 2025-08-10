package com.coffeesprout.api;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.service.AuditService;
import com.coffeesprout.service.SafetyConfig;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/safety")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Safety", description = "Safe Mode status and audit endpoints")
public class SafetyController {
    
    private static final Logger LOG = LoggerFactory.getLogger(SafetyController.class);
    
    @Inject
    SafetyConfig safetyConfig;
    
    @Inject
    AuditService auditService;
    
    @GET
    @Path("/status")
    @Operation(summary = "Get safety status", description = "Get the current safe mode configuration and statistics")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Safety status retrieved successfully",
            content = @Content(schema = @Schema(implementation = SafetyStatusResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve safety status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getSafetyStatus() {
        try {
            AuditService.SafetyStatistics stats = auditService.getStatistics();
            
            SafetyStatusResponse response = new SafetyStatusResponse(
                safetyConfig.enabled(),
                safetyConfig.mode().name(),
                new SafetyStatistics(
                    stats.totalOperations(),
                    stats.blockedOperations(),
                    stats.overriddenOperations(),
                    stats.lastBlocked() != null ? stats.lastBlocked().toString() : null
                )
            );
            
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Failed to get safety status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get safety status: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/audit")
    @Operation(summary = "Get audit log", description = "Query the safety audit log")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Audit log retrieved successfully",
            content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve audit log",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getAuditLog(
            @Parameter(description = "Start time for audit entries (ISO-8601 format)", required = false)
            @QueryParam("startTime") String startTimeStr,
            @Parameter(description = "Maximum number of entries to return", required = false)
            @DefaultValue("100") @QueryParam("limit") int limit) {
        try {
            Instant startTime = Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS); // Default to last 24 hours
            
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                try {
                    startTime = Instant.parse(startTimeStr);
                } catch (DateTimeParseException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("Invalid startTime format. Use ISO-8601 format (e.g., 2024-01-15T00:00:00Z)"))
                            .build();
                }
            }
            
            List<AuditService.AuditEntry> entries = auditService.getAuditEntries(startTime);
            
            // Apply limit
            if (entries.size() > limit) {
                entries = entries.subList(0, limit);
            }
            
            // Convert to response DTOs
            List<AuditEntryResponse> auditResponses = entries.stream()
                .map(entry -> new AuditEntryResponse(
                    entry.timestamp().toString(),
                    entry.operation(),
                    entry.decision(),
                    entry.reason(),
                    entry.vmId(),
                    null, // VM name not currently tracked
                    entry.user(),
                    entry.clientIp()
                ))
                .toList();
            
            AuditLogResponse response = new AuditLogResponse(auditResponses);
            
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Failed to get audit log", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get audit log: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/config")
    @Operation(summary = "Get safety configuration", description = "Get the current safe mode configuration")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Configuration retrieved successfully",
            content = @Content(schema = @Schema(implementation = SafetyConfigResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve configuration",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getSafetyConfig() {
        try {
            SafetyConfigResponse response = new SafetyConfigResponse(
                safetyConfig.enabled(),
                safetyConfig.mode().name(),
                safetyConfig.tagName(),
                safetyConfig.allowUntaggedRead(),
                safetyConfig.allowManualOverride(),
                safetyConfig.auditLog()
            );
            
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Failed to get safety config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get safety config: " + e.getMessage()))
                    .build();
        }
    }
    
    // Response DTOs
    public record SafetyStatusResponse(
        boolean enabled,
        String mode,
        SafetyStatistics statistics
    ) {}
    
    public record SafetyStatistics(
        long totalOperations,
        long blockedOperations,
        long overriddenOperations,
        String lastBlocked
    ) {}
    
    public record AuditLogResponse(
        List<AuditEntryResponse> entries
    ) {}
    
    public record AuditEntryResponse(
        String timestamp,
        String operation,
        String decision,
        String reason,
        Integer vmId,
        String vmName,
        String user,
        String clientIp
    ) {}
    
    public record SafetyConfigResponse(
        boolean enabled,
        String mode,
        String tagName,
        boolean allowUntaggedRead,
        boolean allowManualOverride,
        boolean auditLog
    ) {}
}