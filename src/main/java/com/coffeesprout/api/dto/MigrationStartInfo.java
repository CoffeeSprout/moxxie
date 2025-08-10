package com.coffeesprout.api.dto;

import com.coffeesprout.service.MigrationService.LocalDiskDetectionResult;

/**
 * Internal DTO for passing migration start information between methods
 */
public record MigrationStartInfo(
    Long migrationId,
    String taskUpid,
    int vmId,
    String vmName,
    String sourceNode,
    String targetNode,
    boolean wasRunning,
    boolean wasAutoDetected,
    boolean needsLocalDiskMigration,
    LocalDiskDetectionResult detectionResult
) {}