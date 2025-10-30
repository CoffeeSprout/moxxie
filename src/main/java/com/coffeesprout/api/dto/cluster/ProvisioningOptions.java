package com.coffeesprout.api.dto.cluster;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Options controlling cluster provisioning behavior")
public record ProvisioningOptions(
    @Schema(description = "Start VMs after creation", defaultValue = "false")
    Boolean startAfterCreation,

    @Schema(description = "Provision nodes in parallel", defaultValue = "true")
    Boolean parallelProvisioning,

    @Schema(description = "Maximum parallel operations", example = "5", defaultValue = "5")
    Integer maxParallelOperations,

    @Schema(description = "Rollback strategy on failure", defaultValue = "FULL")
    RollbackStrategy rollbackStrategy,

    @Schema(description = "Starting VM ID for range allocation", example = "10700")
    Integer vmIdRangeStart,

    @Schema(description = "Dry run mode - validate without creating VMs", defaultValue = "false")
    Boolean dryRun
) {
    public ProvisioningOptions {
        if (startAfterCreation == null) {
            startAfterCreation = false;
        }
        if (parallelProvisioning == null) {
            parallelProvisioning = true;
        }
        if (maxParallelOperations == null) {
            maxParallelOperations = 5;
        }
        if (rollbackStrategy == null) {
            rollbackStrategy = RollbackStrategy.FULL;
        }
        if (dryRun == null) {
            dryRun = false;
        }
    }

    @Schema(description = "Strategies for handling provisioning failures")
    public enum RollbackStrategy {
        @Schema(description = "Delete all VMs on any failure")
        FULL,

        @Schema(description = "Keep successfully created VMs")
        PARTIAL,

        @Schema(description = "No rollback, leave all VMs")
        NONE,

        @Schema(description = "Mark failed VMs but keep them for debugging")
        MARK_FAILED
    }
}
