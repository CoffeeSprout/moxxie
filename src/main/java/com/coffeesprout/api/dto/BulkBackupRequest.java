package com.coffeesprout.api.dto;

import java.util.List;

import jakarta.validation.constraints.*;

import com.coffeesprout.scheduler.model.VMSelector;

/**
 * Request DTO for bulk backup operations
 */
public record BulkBackupRequest(
    @NotNull(message = "VM selectors are required") @Size(min = 1, message = "At least one VM selector is required")
    List<VMSelector> vmSelectors,

    @NotBlank(message = "Storage is required")
    String storage,

    @Pattern(regexp = "snapshot|suspend|stop", message = "Mode must be snapshot, suspend, or stop")
    String mode,

    @Pattern(regexp = "0|1|gzip|lzo|zstd", message = "Invalid compression format")
    String compress,

    @Size(max = 8192, message = "Notes cannot exceed 8192 characters")
    String notes,

    @Min(value = 1, message = "TTL must be at least 1 day")
    @Max(value = 3653, message = "TTL cannot exceed 3653 days (10 years)")
    Integer ttlDays,

    @Min(value = 0, message = "Remove older must be non-negative")
    @Max(value = 365, message = "Remove older cannot exceed 365 days")
    Integer removeOlder,

    Boolean protectBackup,

    @Pattern(regexp = "always|failure", message = "Mail notification must be 'always' or 'failure'")
    String mailNotification,

    @Min(value = 1, message = "Max parallel must be at least 1")
    @Max(value = 20, message = "Max parallel cannot exceed 20")
    Integer maxParallel,

    Boolean dryRun
) {
    /**
     * Constructor with defaults
     */
    public BulkBackupRequest {
        if (mode == null) {
            mode = "snapshot";
        }
        if (compress == null) {
            compress = "zstd";
        }
        if (maxParallel == null) {
            maxParallel = 3;
        }
        if (dryRun == null) {
            dryRun = false;
        }
        if (protectBackup == null) {
            protectBackup = false;
        }
    }
}
