package com.coffeesprout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to restore a VM from backup")
public record RestoreRequest(
    @Schema(description = "Backup volume ID to restore from", required = true,
            example = "local:backup/vzdump-qemu-100-2024_01_15-10_30_00.vma.zst")
    @NotBlank(message = "Backup volume ID is required")
    String backup,
    
    @Schema(description = "Target VM ID for restore", required = true, example = "101", minimum = "100")
    @NotNull(message = "VM ID is required")
    @Min(value = 100, message = "VM ID must be at least 100")
    @Max(value = 999999999, message = "VM ID must be less than 999999999")
    Integer vmId,
    
    @Schema(description = "Target node for the restored VM", required = true, example = "pve1")
    @NotBlank(message = "Target node is required")
    String targetNode,
    
    @Schema(description = "Target storage for VM disks", example = "local-lvm")
    String targetStorage,
    
    @Schema(description = "Start VM after restore completes", defaultValue = "false")
    Boolean startAfterRestore,
    
    @Schema(description = "Regenerate unique properties (MAC addresses)", defaultValue = "true")
    Boolean unique,
    
    @Schema(description = "Overwrite existing VM with same ID", defaultValue = "false")
    Boolean overwriteExisting,
    
    @Schema(description = "Bandwidth limit in MB/s", example = "100", minimum = "0")
    @Min(value = 0, message = "Bandwidth must be >= 0")
    @Max(value = 10000, message = "Bandwidth must be <= 10000")
    Integer bandwidth,
    
    @Schema(description = "Name for the restored VM", example = "restored-vm")
    String name,
    
    @Schema(description = "Description for the restored VM")
    String description
) {
    public RestoreRequest {
        // Set defaults
        if (startAfterRestore == null) {
            startAfterRestore = false;
        }
        if (unique == null) {
            unique = true;
        }
        if (overwriteExisting == null) {
            overwriteExisting = false;
        }
    }
}