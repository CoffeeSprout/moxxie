package com.coffeesprout.api.dto;

import java.time.Instant;

/**
 * Response DTO for VM migration operation
 */
public record MigrationResponse(
    Long migrationId,
    String taskUpid,
    String message,
    Integer vmId,
    String vmName,
    String sourceNode,
    String targetNode,
    String migrationType,
    Instant startedAt
) {
    /**
     * Simple constructor for successful migration start
     */
    public MigrationResponse(Long migrationId, String taskUpid, String message) {
        this(migrationId, taskUpid, message, null, null, null, null, null, null);
    }
}