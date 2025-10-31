package com.coffeesprout.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.coffeesprout.util.UnitConverter;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response for node drain/undrain operations with progress tracking.
 */
@RegisterForReflection
public record NodeDrainResponse(
    String drainId,
    String node,
    String operation,
    String status,
    Integer totalVMs,
    Integer completedVMs,
    Integer failedVMs,
    List<VMMigrationStatus> vmStatus,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String message
) {
    /**
     * Status of individual VM migration during drain.
     */
    @RegisterForReflection
    public record VMMigrationStatus(
        Integer vmid,
        String name,
        String status,
        String targetNode,
        String error
    ) {}

    /**
     * Calculate progress percentage.
     */
    public int progressPercent() {
        if (totalVMs == null || totalVMs == 0) {
            return 0;
        }
        int completed = completedVMs != null ? completedVMs : 0;
        return (int) ((completed * UnitConverter.Percentage.PERCENT_MULTIPLIER) / totalVMs);
    }

    /**
     * Check if drain is complete.
     */
    public boolean isComplete() {
        return "completed".equals(status) || "failed".equals(status);
    }

    /**
     * Check if drain is in progress.
     */
    public boolean isInProgress() {
        return "in_progress".equals(status);
    }
}
