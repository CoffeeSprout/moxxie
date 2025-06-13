package com.coffeesprout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record BackupCleanupRequest(
    @NotBlank(message = "Retention policy is required")
    @Pattern(regexp = "(days|count|monthly):[0-9]+", message = "Invalid retention policy format. Use: days:30, count:5, or monthly:3")
    String retentionPolicy,    // "days:30", "count:5", "monthly:3"
    
    List<String> tags,         // Only cleanup VMs with these tags
    List<Integer> vmIds,       // Specific VMs to cleanup
    boolean dryRun,           // Preview what would be deleted
    boolean ignoreProtected   // Skip protected backups
) {}