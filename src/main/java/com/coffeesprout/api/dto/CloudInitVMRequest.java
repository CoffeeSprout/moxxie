package com.coffeesprout.api.dto;

import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for creating a VM from a cloud-init image
 */
@Schema(description = "Request to create a VM from a cloud-init image")
public record CloudInitVMRequest(
    @Schema(description = "VM ID (100-999999999)", example = "100", required = true)
    @NotNull(message = "VM ID is required")
    @Min(value = 100, message = "VM ID must be at least 100")
    @Max(value = 999999999, message = "VM ID must be less than 999999999")
    Integer vmid,
    
    @Schema(description = "VM name", example = "k8s-control-01", required = true)
    @NotBlank(message = "VM name is required")
    @Size(max = 63, message = "VM name must not exceed 63 characters")
    @Pattern(regexp = "^[a-zA-Z0-9.-]+$", message = "VM name can only contain alphanumeric characters, dots, and dashes")
    String name,
    
    @Schema(description = "Target node", example = "hv7", required = true)
    @NotBlank(message = "Node is required")
    String node,
    
    @Schema(description = "Number of CPU cores", example = "4", required = true)
    @NotNull(message = "CPU cores is required")
    @Min(value = 1, message = "At least 1 CPU core is required")
    @Max(value = 128, message = "Cannot exceed 128 CPU cores")
    Integer cores,
    
    @Schema(description = "Memory in MB", example = "8192", required = true)
    @NotNull(message = "Memory is required")
    @Min(value = 512, message = "At least 512 MB of memory is required")
    @Max(value = 1048576, message = "Cannot exceed 1TB of memory")
    Integer memoryMB,
    
    @Schema(description = "Cloud image source", example = "util-iso:iso/debian-12-generic-amd64.qcow2", required = true)
    @NotBlank(message = "Image source is required")
    @Pattern(regexp = "^[a-zA-Z0-9-]+:((iso|vztmpl|images|\\d+)/)?[a-zA-Z0-9._-]+$", 
             message = "Image source must be in format storage:content-type/filename, storage:vmid/filename, or storage:base-vmid-disk-N")
    String imageSource,
    
    @Schema(description = "Target storage for the VM disk", example = "local-zfs", required = true)
    @NotBlank(message = "Target storage is required")
    String targetStorage,
    
    @Schema(description = "Disk size in GB (will expand the imported image)", example = "50")
    @Min(value = 0, message = "Disk size cannot be negative")
    @Max(value = 65536, message = "Cannot exceed 64TB disk size")
    Integer diskSizeGB,
    
    @Schema(description = "Cloud-init user", example = "debian")
    String cloudInitUser,
    
    @Schema(description = "Cloud-init password")
    String cloudInitPassword,
    
    @Schema(description = "SSH public keys (one per line)", example = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGLmQqfp8X5DUVxLruBsCmJ7m4mDGcr5V7e2BXMkNPDp user@example.com")
    String sshKeys,
    
    @Schema(description = "Network configuration", required = true)
    @NotNull(message = "Network configuration is required")
    NetworkConfig network,
    
    @Schema(description = "IP configuration (e.g., 'ip=dhcp' or 'ip=192.168.1.100/24,gw=192.168.1.1')", 
            example = "ip=dhcp")
    String ipConfig,
    
    @Schema(description = "DNS search domain", example = "cluster.local")
    String searchDomain,
    
    @Schema(description = "DNS nameservers (comma-separated)", example = "8.8.8.8,8.8.4.4")
    String nameservers,
    
    @Schema(description = "CPU type", example = "host", defaultValue = "kvm64")
    String cpuType,
    
    @Schema(description = "Enable QEMU agent", defaultValue = "true")
    Boolean qemuAgent,
    
    @Schema(description = "Start VM after creation", defaultValue = "false")
    Boolean start,
    
    @Schema(description = "VM description")
    String description,
    
    @Schema(description = "Tags to apply to the VM", example = "k8s-control,env-prod")
    String tags,
    
    @Schema(description = "Additional disk options (SSD, discard, etc)")
    DiskOptions diskOptions
) {
    
    /**
     * Network configuration
     */
    @Schema(description = "Network configuration for the VM")
    public record NetworkConfig(
        @Schema(description = "Network model", example = "virtio", defaultValue = "virtio")
        String model,
        
        @Schema(description = "Network bridge", example = "vmbr0", required = true)
        @NotBlank(message = "Network bridge is required")
        String bridge,
        
        @Schema(description = "VLAN tag", example = "100")
        @Min(value = 1, message = "VLAN must be between 1 and 4094")
        @Max(value = 4094, message = "VLAN must be between 1 and 4094")
        Integer vlanTag
    ) {
        public NetworkConfig {
            if (model == null) {
                model = "virtio";
            }
        }
    }
    
    /**
     * Additional disk configuration options
     */
    @Schema(description = "Additional disk options for performance tuning")
    public record DiskOptions(
        @Schema(description = "Enable SSD emulation", defaultValue = "false")
        Boolean ssd,
        
        @Schema(description = "Enable discard/TRIM", defaultValue = "false")
        Boolean discard,
        
        @Schema(description = "Enable IO thread", defaultValue = "false")
        Boolean iothread,
        
        @Schema(description = "Cache mode")
        DiskConfig.CacheMode cache
    ) {}
    
    /**
     * Convert to DiskConfig for the imported disk
     */
    public DiskConfig toDiskConfig() {
        return new DiskConfig(
            DiskConfig.DiskInterface.SCSI,
            0, // slot 0
            targetStorage,
            diskSizeGB != null ? diskSizeGB : 0,
            diskOptions != null ? diskOptions.ssd : null,
            diskOptions != null ? diskOptions.iothread : null,
            diskOptions != null ? diskOptions.cache : null,
            diskOptions != null ? diskOptions.discard : null,
            null, // format
            true, // backup
            false, // replicate
            imageSource, // importFrom
            null // additionalOptions
        );
    }
}