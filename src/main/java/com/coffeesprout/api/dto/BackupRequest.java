package com.coffeesprout.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to create a VM backup")
public record BackupRequest(
    @Schema(description = "Target storage for backup", required = true, example = "local")
    @NotBlank(message = "Storage is required")
    String storage,

    @Schema(description = "Backup mode", defaultValue = "snapshot",
            enumeration = {"snapshot", "suspend", "stop"})
    @Pattern(regexp = "^(snapshot|suspend|stop)$",
             message = "Mode must be one of: snapshot, suspend, stop")
    String mode,

    @Schema(description = "Compression type", defaultValue = "zstd",
            enumeration = {"none", "lzo", "gzip", "zstd"})
    @Pattern(regexp = "^(none|lzo|gzip|zstd)$",
             message = "Compress must be one of: none, lzo, gzip, zstd")
    String compress,

    @Schema(description = "Backup notes/description", example = "Pre-upgrade backup", maxLength = 1024)
    String notes,

    @Schema(description = "Protect backup from accidental deletion", defaultValue = "false")
    Boolean protectBackup,

    @Schema(description = "Remove backups older than N (days)", example = "7", minimum = "0")
    @Min(value = 0, message = "removeOlder must be >= 0")
    Integer removeOlder,

    @Schema(description = "When to send notifications", defaultValue = "always",
            enumeration = {"always", "failure"})
    @Pattern(regexp = "^(always|failure)$",
             message = "notificationMode must be one of: always, failure")
    String notificationMode
) {
    public BackupRequest {
        // Set defaults
        if (mode == null) {
            mode = "snapshot";
        }
        if (compress == null) {
            compress = "zstd";
        }
        if (protectBackup == null) {
            protectBackup = false;
        }
        if (notificationMode == null) {
            notificationMode = "always";
        }
    }
}
