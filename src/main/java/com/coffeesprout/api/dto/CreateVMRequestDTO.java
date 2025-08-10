package com.coffeesprout.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

@Schema(description = "Request to create a new VM")
public record CreateVMRequestDTO(
    @Schema(description = "VM ID (optional, will be auto-generated if not provided)", example = "101")
    @Min(value = 100, message = "VM ID must be at least 100")
    @Max(value = 999999999, message = "VM ID must be less than 999999999")
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
    
    @Schema(description = "Disk size in GB (deprecated, use disks instead)", example = "100")
    @Min(value = 1, message = "Disk size must be at least 1 GB")
    @Max(value = 65536, message = "Cannot exceed 64TB disk size")
    @Deprecated
    Integer diskGB,
    
    @Schema(description = "Disk configurations for the VM")
    @Valid
    java.util.List<DiskConfig> disks,
    
    @Schema(description = "Network configurations (supports multiple NICs)")
    @Valid
    java.util.List<com.coffeesprout.api.dto.NetworkConfig> networks,
    
    @Schema(description = "Network configuration (deprecated, use 'networks' instead)")
    @Valid
    @Deprecated
    NetworkConfig network,
    
    @Schema(description = "Start VM on boot", example = "true")
    Boolean startOnBoot,
    
    @Schema(description = "Resource pool to place the VM in (optional)", example = "moxxie-pool")
    String pool,
    
    @Schema(description = "Client identifier for VLAN assignment", example = "client1")
    String clientId,
    
    @Schema(description = "Project name for VNet naming", example = "web-app")
    String project,
    
    @Schema(description = "Tags to apply to the VM (will be auto-tagged with moxxie and other tags based on name)", example = "[\"env-prod\", \"always-on\"]")
    java.util.List<String> tags,
    
    @Schema(description = "Boot order configuration (e.g., 'order=scsi0;net0' for PXE boot fallback)", example = "order=net0;scsi0")
    String bootOrder,
    
    @Schema(description = "CPU type (e.g., 'host', 'kvm64', 'x86-64-v2-AES')", example = "host", defaultValue = "x86-64-v2-AES")
    String cpuType,
    
    @Schema(description = "VGA hardware type (e.g., 'std', 'serial0', 'qxl', 'virtio')", example = "std", defaultValue = "std")
    String vgaType
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