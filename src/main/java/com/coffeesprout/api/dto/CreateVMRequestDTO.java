package com.coffeesprout.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to create a new VM")
public record CreateVMRequestDTO(
    @Schema(description = "VM ID (optional, will be auto-generated if not provided)", example = "101")
    Integer vmId,
    
    @Schema(description = "VM name", example = "web-server-01", required = true)
    @NotBlank(message = "VM name is required")
    @Size(max = 63, message = "VM name must not exceed 63 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-_.]+$", message = "VM name can only contain letters, numbers, hyphens, underscores, and dots")
    String name,
    
    @Schema(description = "Node to create VM on", example = "pve1", required = true)
    @NotBlank(message = "Node is required")
    String node,
    
    @Schema(description = "Template to use for VM creation", example = "debian-12")
    String template,
    
    @Schema(description = "Number of CPU cores", example = "4", required = true)
    @NotNull(message = "CPU cores is required")
    @Min(value = 1, message = "Must have at least 1 CPU core")
    @Max(value = 128, message = "Cannot exceed 128 CPU cores")
    Integer cores,
    
    @Schema(description = "Memory in MB", example = "8192", required = true)
    @NotNull(message = "Memory is required")
    @Min(value = 512, message = "Must have at least 512 MB of memory")
    @Max(value = 1048576, message = "Cannot exceed 1TB of memory")
    Integer memoryMB,
    
    @Schema(description = "Disk size in GB", example = "100")
    @Min(value = 1, message = "Disk size must be at least 1 GB")
    @Max(value = 65536, message = "Cannot exceed 64TB disk size")
    Integer diskGB,
    
    @Schema(description = "Network configuration")
    @Valid
    NetworkConfig network,
    
    @Schema(description = "Start VM on boot", example = "true")
    Boolean startOnBoot,
    
    @Schema(description = "Resource pool to place the VM in (optional)", example = "moxxie-pool")
    String pool,
    
    @Schema(description = "Client identifier for VLAN assignment", example = "client1")
    String clientId,
    
    @Schema(description = "Project name for VNet naming", example = "web-app")
    String project
) {
    @Schema(description = "Network configuration for the VM")
    public record NetworkConfig(
        @Schema(description = "Network bridge", example = "vmbr0", required = true)
        @NotBlank(message = "Network bridge is required")
        String bridge,
        
        @Schema(description = "VLAN tag", example = "100")
        @Min(value = 1, message = "VLAN must be between 1 and 4094")
        @Max(value = 4094, message = "VLAN must be between 1 and 4094")
        Integer vlan
    ) {}
}