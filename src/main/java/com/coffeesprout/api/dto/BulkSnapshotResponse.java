package com.coffeesprout.api.dto;

import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Response for bulk snapshot creation operation")
public record BulkSnapshotResponse(
    @Schema(description = "Results per VM ID",
            example = "{\"8200\": {\"status\": \"success\", \"taskId\": \"UPID:node1:...\", \"snapshotName\": \"bulk-8200-20240621\"}, \"8201\": {\"status\": \"error\", \"error\": \"VM is locked\"}}")
    Map<Integer, SnapshotResult> results,

    @Schema(description = "Summary message",
            example = "Created snapshots for 8/10 VMs successfully")
    String summary,

    @Schema(description = "Total number of VMs processed", example = "10")
    int totalVMs,

    @Schema(description = "Number of successful snapshots", example = "8")
    int successCount,

    @Schema(description = "Number of failed snapshots", example = "2")
    int failureCount,

    @Schema(description = "Whether this was a dry run", example = "false")
    boolean dryRun
) {
    @RegisterForReflection
    @Schema(description = "Result of snapshot creation for a single VM")
    public record SnapshotResult(
        @Schema(description = "Status of the operation", example = "success")
        String status,

        @Schema(description = "Task ID for successful operations",
                example = "UPID:node1:00001234:12345678:12345678:qmcreate:100:root@pam:")
        String taskId,

        @Schema(description = "Name of the created snapshot", example = "bulk-8200-20240621")
        String snapshotName,

        @Schema(description = "Error message for failed operations",
                example = "VM is locked by backup process")
        String error,

        @Schema(description = "VM name for reference", example = "web-server-01")
        String vmName
    ) {
        public static SnapshotResult success(String taskId, String snapshotName, String vmName) {
            return new SnapshotResult("success", taskId, snapshotName, null, vmName);
        }

        public static SnapshotResult error(String error, String vmName) {
            return new SnapshotResult("error", null, null, error, vmName);
        }

        public static SnapshotResult dryRun(String snapshotName, String vmName) {
            return new SnapshotResult("dry-run", null, snapshotName, null, vmName);
        }
    }
}
