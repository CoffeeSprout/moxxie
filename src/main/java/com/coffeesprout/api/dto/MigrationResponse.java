package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.List;

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
    Instant startedAt,
    Boolean autoDetectedLocalDisks,
    List<String> localStoragePools,
    String detectionMethod
) {
    /**
     * Simple constructor for successful migration start
     */
    public MigrationResponse(Long migrationId, String taskUpid, String message) {
        this(migrationId, taskUpid, message, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * Constructor for backward compatibility (without auto-detection fields)
     */
    public MigrationResponse(Long migrationId, String taskUpid, String message,
                           Integer vmId, String vmName, String sourceNode, 
                           String targetNode, String migrationType, Instant startedAt) {
        this(migrationId, taskUpid, message, vmId, vmName, sourceNode, 
             targetNode, migrationType, startedAt, null, null, null);
    }
}