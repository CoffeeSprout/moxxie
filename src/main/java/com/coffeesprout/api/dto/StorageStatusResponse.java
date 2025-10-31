package com.coffeesprout.api.dto;

import com.coffeesprout.util.UnitConverter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for detailed storage status including usage and health
 */
@Schema(description = "Detailed storage status information")
public record StorageStatusResponse(
    @Schema(description = "Storage identifier", example = "local")
    String storage,

    @Schema(description = "Storage type", example = "dir")
    String type,

    @Schema(description = "Whether storage is currently active")
    boolean active,

    @Schema(description = "Whether storage is enabled")
    boolean enabled,

    @Schema(description = "Total capacity in bytes")
    long total,

    @Schema(description = "Used space in bytes")
    long used,

    @Schema(description = "Available space in bytes")
    long available,

    @JsonProperty("used_percentage")
    @Schema(description = "Percentage of storage used", example = "45.2")
    double usedPercentage,

    @JsonProperty("content_types")
    @Schema(description = "Supported content types",
            example = "[\"images\", \"iso\", \"vztmpl\", \"backup\"]")
    String[] contentTypes,

    @JsonProperty("is_shared")
    @Schema(description = "Whether storage is shared across cluster")
    boolean isShared,

    @JsonProperty("nodes")
    @Schema(description = "Nodes where this storage is available",
            example = "[\"pve1\", \"pve2\"]")
    String[] nodes,

    @JsonProperty("health_status")
    @Schema(description = "Storage health status",
            enumeration = {"healthy", "warning", "critical", "unknown"})
    String healthStatus,

    @JsonProperty("formatted_total")
    @Schema(description = "Human-readable total size", example = "UnitConverter.Percentage.PERCENT_MULTIPLIER GB")
    String formattedTotal,

    @JsonProperty("formatted_used")
    @Schema(description = "Human-readable used size", example = "45.2 GB")
    String formattedUsed,

    @JsonProperty("formatted_available")
    @Schema(description = "Human-readable available size", example = "54.8 GB")
    String formattedAvailable
) {

    /**
     * Determine health status based on usage percentage
     */
    public static String calculateHealthStatus(double usedPercentage) {
        if (usedPercentage >= 95) {
            return "critical";
        } else if (usedPercentage >= 85) {
            return "warning";
        } else {
            return "healthy";
        }
    }

    /**
     * Create a StorageStatusResponse with calculated fields
     */
    public static StorageStatusResponse create(String storage, String type, boolean active,
                                              boolean enabled, long total, long used,
                                              long available, String[] contentTypes,
                                              boolean isShared, String[] nodes) {
        double usedPct = total > 0 ? (used * UnitConverter.Percentage.PERCENT_MULTIPLIER / total) : 0;
        String health = calculateHealthStatus(usedPct);

        return new StorageStatusResponse(
            storage,
            type,
            active,
            enabled,
            total,
            used,
            available,
            usedPct,
            contentTypes,
            isShared,
            nodes,
            health,
            UnitConverter.formatBytes(total),
            UnitConverter.formatBytes(used),
            UnitConverter.formatBytes(available)
        );
    }
}
