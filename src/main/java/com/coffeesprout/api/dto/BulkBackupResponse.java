package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for bulk backup operations
 */
public record BulkBackupResponse(
    Map<Integer, BackupResult> results,
    String summary,
    int totalVMs,
    int successCount,
    int failureCount,
    int skippedCount,
    boolean dryRun,
    Instant startTime,
    Instant endTime,
    long durationSeconds
) {
    /**
     * Result for a single VM backup operation
     */
    public record BackupResult(
        String status, // success, error, skipped, dry-run
        String taskId,
        String message,
        String vmName,
        String node,
        String storage,
        Instant timestamp
    ) {
        public static BackupResult success(String taskId, String vmName, String node, String storage) {
            return new BackupResult(
                "success",
                taskId,
                "Backup task started successfully",
                vmName,
                node,
                storage,
                Instant.now()
            );
        }
        
        public static BackupResult error(String message, String vmName, String node) {
            return new BackupResult(
                "error",
                null,
                message,
                vmName,
                node,
                null,
                Instant.now()
            );
        }
        
        public static BackupResult skipped(String reason, String vmName, String node) {
            return new BackupResult(
                "skipped",
                null,
                reason,
                vmName,
                node,
                null,
                Instant.now()
            );
        }
        
        public static BackupResult dryRun(String vmName, String node, String storage) {
            return new BackupResult(
                "dry-run",
                null,
                "Would create backup",
                vmName,
                node,
                storage,
                Instant.now()
            );
        }
    }
}