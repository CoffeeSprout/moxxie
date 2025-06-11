package com.coffeesprout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to create a VM snapshot")
public record CreateSnapshotRequest(
    @Schema(description = "Snapshot name", required = true, example = "pre-upgrade", 
            pattern = "^[a-zA-Z0-9_-]+$")
    @NotBlank(message = "Snapshot name is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Snapshot name must contain only alphanumeric characters, hyphens, and underscores")
    String name,
    
    @Schema(description = "Snapshot description", example = "Snapshot before major upgrade", maxLength = 1024)
    @Size(max = 1024, message = "Description must not exceed 1024 characters")
    String description,
    
    @Schema(description = "Include VM RAM state in snapshot", defaultValue = "false")
    Boolean includeVmState
) {
    public CreateSnapshotRequest {
        // Default includeVmState to false if not provided
        if (includeVmState == null) {
            includeVmState = false;
        }
    }
}