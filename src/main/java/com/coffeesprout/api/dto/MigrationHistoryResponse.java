package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.Map;

import com.coffeesprout.model.VmMigration;

/**
 * Response DTO for VM migration history
 */
public record MigrationHistoryResponse(
    Long id,
    Integer vmId,
    String vmName,
    String sourceNode,
    String targetNode,
    String migrationType,
    String preMigrationState,
    String postMigrationState,
    Instant startedAt,
    Instant completedAt,
    Integer durationSeconds,
    String status,
    String errorMessage,
    String taskUpid,
    String initiatedBy,
    Map<String, Object> options
) {
    /**
     * Create from entity
     */
    public static MigrationHistoryResponse fromEntity(VmMigration entity) {
        return new MigrationHistoryResponse(
            entity.id,
            entity.vmId,
            entity.vmName,
            entity.sourceNode,
            entity.targetNode,
            entity.migrationType,
            entity.preMigrationState,
            entity.postMigrationState,
            entity.startedAt,
            entity.completedAt,
            entity.durationSeconds,
            entity.status,
            entity.errorMessage,
            entity.taskUpid,
            entity.initiatedBy,
            entity.options
        );
    }
}
