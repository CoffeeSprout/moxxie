package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "VM snapshot information")
public record SnapshotResponse(
    @Schema(description = "Snapshot name/identifier", required = true, example = "backup-2024-01-15")
    String name,

    @Schema(description = "Snapshot description", example = "Before system upgrade")
    String description,

    @Schema(description = "Parent snapshot name", example = "initial")
    String parent,

    @Schema(description = "Creation timestamp (Unix time)", required = true)
    @JsonProperty("snaptime")
    Long createdAt,

    @Schema(description = "Whether VM state (RAM) is included", required = true)
    @JsonProperty("vmstate")
    Boolean includesVmState,

    @Schema(description = "Snapshot size in bytes (if available)")
    Long size
) {}
