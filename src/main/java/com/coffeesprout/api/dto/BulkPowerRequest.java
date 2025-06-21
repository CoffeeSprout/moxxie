package com.coffeesprout.api.dto;

import com.coffeesprout.scheduler.model.VMSelector;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@RegisterForReflection
@Schema(description = "Request to perform power operations on multiple VMs")
public record BulkPowerRequest(
    @Schema(description = "VM selectors to identify target VMs", required = true)
    @NotNull(message = "VM selectors are required")
    @Size(min = 1, message = "At least one VM selector must be provided")
    @Valid
    List<VMSelector> vmSelectors,
    
    @Schema(description = "Power operation to perform", required = true, 
            enumeration = {"START", "STOP", "SHUTDOWN", "REBOOT", "SUSPEND", "RESUME"})
    @NotNull(message = "Operation is required")
    PowerOperation operation,
    
    @Schema(description = "Force operation (for stop/reboot)", defaultValue = "false")
    Boolean force,
    
    @Schema(description = "Timeout in seconds for graceful operations", 
            defaultValue = "300", example = "300")
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds (1 hour)")
    Integer timeoutSeconds,
    
    @Schema(description = "Maximum number of parallel operations", 
            defaultValue = "5", example = "5")
    @Min(value = 1, message = "Max parallel must be at least 1")
    @Max(value = 20, message = "Max parallel must not exceed 20")
    Integer maxParallel,
    
    @Schema(description = "Skip VMs that are already in desired state", 
            defaultValue = "true")
    Boolean skipIfAlreadyInState,
    
    @Schema(description = "Preview mode - shows what would be done without performing operations", 
            defaultValue = "false")
    Boolean dryRun
) {
    public BulkPowerRequest {
        // Set defaults
        if (force == null) force = false;
        if (timeoutSeconds == null) timeoutSeconds = 300;
        if (maxParallel == null) maxParallel = 5;
        if (skipIfAlreadyInState == null) skipIfAlreadyInState = true;
        if (dryRun == null) dryRun = false;
    }
    
    public enum PowerOperation {
        START,      // Start VM
        STOP,       // Force stop VM
        SHUTDOWN,   // Graceful shutdown
        REBOOT,     // Reboot VM
        SUSPEND,    // Suspend VM
        RESUME      // Resume from suspend
    }
}