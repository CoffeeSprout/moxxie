package com.coffeesprout.api.dto;

import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Response for bulk power operation")
public record BulkPowerResponse(
    @Schema(description = "Results per VM ID",
            example = "{\"8200\": {\"status\": \"success\", \"previousState\": \"stopped\", \"currentState\": \"running\", \"vmName\": \"web-server\"}, \"8201\": {\"status\": \"skipped\", \"reason\": \"Already running\", \"vmName\": \"db-server\"}}")
    Map<Integer, PowerResult> results,

    @Schema(description = "Summary message",
            example = "Started 3/5 VMs successfully (2 skipped)")
    String summary,

    @Schema(description = "Total number of VMs processed", example = "5")
    int totalVMs,

    @Schema(description = "Number of successful operations", example = "3")
    int successCount,

    @Schema(description = "Number of failed operations", example = "0")
    int failureCount,

    @Schema(description = "Number of skipped VMs", example = "2")
    int skippedCount,

    @Schema(description = "Whether this was a dry run", example = "false")
    boolean dryRun
) {
    @RegisterForReflection
    @Schema(description = "Result of power operation for a single VM")
    public record PowerResult(
        @Schema(description = "Status of the operation",
                enumeration = {"success", "error", "skipped", "dry-run"})
        String status,

        @Schema(description = "Previous VM state", example = "stopped")
        String previousState,

        @Schema(description = "Current VM state", example = "running")
        String currentState,

        @Schema(description = "Task ID for async operations",
                example = "UPID:node1:00001234:12345678:12345678:qmstart:100:root@pam:")
        String taskId,

        @Schema(description = "Error message for failed operations",
                example = "VM is locked by backup process")
        String error,

        @Schema(description = "Reason for skipping",
                example = "Already in desired state")
        String reason,

        @Schema(description = "VM name for reference", example = "web-server-01")
        String vmName
    ) {
        public static PowerResult success(String previousState, String currentState,
                                        String taskId, String vmName) {
            return new PowerResult("success", previousState, currentState, taskId,
                                 null, null, vmName);
        }

        public static PowerResult error(String previousState, String error, String vmName) {
            return new PowerResult("error", previousState, null, null, error, null, vmName);
        }

        public static PowerResult skipped(String currentState, String reason, String vmName) {
            return new PowerResult("skipped", currentState, currentState, null,
                                 null, reason, vmName);
        }

        public static PowerResult dryRun(String currentState, String targetState,
                                       String vmName) {
            return new PowerResult("dry-run", currentState, targetState, null,
                                 null, null, vmName);
        }
    }
}
