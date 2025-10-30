package com.coffeesprout.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

/**
 * Response containing node maintenance status and history.
 */
@RegisterForReflection
public record NodeMaintenanceResponse(
    String node,
    Boolean inMaintenance,
    String reason,
    LocalDateTime maintenanceStarted,
    LocalDateTime maintenanceEnded,
    Integer vmsOnNode,
    String lastDrainId,
    String drainStatus
) {
    /**
     * Check if node is currently in maintenance.
     */
    public boolean isInMaintenance() {
        return inMaintenance != null && inMaintenance;
    }

    /**
     * Get maintenance duration in minutes.
     */
    public Long maintenanceDurationMinutes() {
        if (maintenanceStarted == null) {
            return null;
        }
        LocalDateTime end = maintenanceEnded != null ? maintenanceEnded : LocalDateTime.now();
        return java.time.Duration.between(maintenanceStarted, end).toMinutes();
    }
}
