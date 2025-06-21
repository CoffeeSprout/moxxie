package com.coffeesprout.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for VM migration
 */
public record MigrationRequest(
    @NotBlank(message = "Target node is required")
    String targetNode,
    
    Boolean allowOfflineMigration,  // Default false - fail if online migration not possible
    Boolean withLocalDisks,         // Default false - migrate with local disks
    Boolean force,                  // Default false - force migration of VMs with local devices
    Integer bwlimit,               // Bandwidth limit in KiB/s
    String targetStorage,          // Storage mapping (single storage ID or mapping)
    String migrationType,          // "secure" (default) or "insecure"
    String migrationNetwork        // CIDR for migration network
) {
    /**
     * Constructor with defaults
     */
    public MigrationRequest {
        // Set defaults
        if (allowOfflineMigration == null) {
            allowOfflineMigration = false;
        }
        if (withLocalDisks == null) {
            withLocalDisks = false;
        }
        if (force == null) {
            force = false;
        }
        if (migrationType == null) {
            migrationType = "secure";
        }
    }
}