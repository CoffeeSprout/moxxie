package com.coffeesprout.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import com.coffeesprout.scheduler.model.VMSelector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Request to create snapshots for multiple VMs")
public record BulkSnapshotRequest(
    @Schema(description = "VM selectors to identify target VMs", required = true)
    @NotNull(message = "VM selectors are required") @Size(min = 1, message = "At least one VM selector must be provided")
    @Valid
    List<VMSelector> vmSelectors,

    @Schema(description = "Snapshot name pattern. Supports placeholders: {vm}, {date}, {time}",
            required = true, example = "bulk-{vm}-{date}")
    @NotBlank(message = "Snapshot name is required")
    @Size(max = 40, message = "Snapshot name pattern must not exceed 40 characters")
    String snapshotName,

    @Schema(description = "Description for the snapshots", example = "Pre-update snapshot")
    @Size(max = 200, message = "Description must not exceed 200 characters")
    String description,

    @Schema(description = "Time-to-live in hours (1-8760). Snapshots will be auto-deleted after this time.",
            example = "24")
    @Min(value = 1, message = "TTL must be at least 1 hour")
    @Max(value = 8760, message = "TTL must not exceed 8760 hours (1 year)")
    Integer ttlHours,

    @Schema(description = "Include VM memory state in snapshot", defaultValue = "false")
    Boolean includeMemory,

    @Schema(description = "Maximum number of parallel snapshot operations",
            defaultValue = "5", example = "5")
    @Min(value = 1, message = "Max parallel must be at least 1")
    @Max(value = 20, message = "Max parallel must not exceed 20")
    Integer maxParallel,

    @Schema(description = "Preview mode - shows what would be done without creating snapshots",
            defaultValue = "false")
    Boolean dryRun
) {
    public BulkSnapshotRequest {
        // Set defaults
        if (includeMemory == null) includeMemory = false;
        if (maxParallel == null) maxParallel = 5;
        if (dryRun == null) dryRun = false;
    }
}
