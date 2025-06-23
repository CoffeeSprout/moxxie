package com.coffeesprout.api.dto;

import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Configuration for a VM disk
 */
@Schema(description = "Configuration for a virtual disk")
public record DiskConfig(
    @Schema(description = "Disk interface type", example = "scsi", required = true)
    @NotNull(message = "Disk interface is required")
    DiskInterface interfaceType,
    
    @Schema(description = "Disk slot number (e.g., 0 for scsi0, 1 for scsi1)", example = "0", required = true)
    @NotNull(message = "Disk slot is required")
    @Min(value = 0, message = "Slot must be >= 0")
    @Max(value = 30, message = "Slot must be <= 30")  // Max for SCSI
    Integer slot,
    
    @Schema(description = "Storage pool ID", example = "local-lvm", required = true)
    @NotBlank(message = "Storage is required")
    String storage,
    
    @Schema(description = "Disk size in GB", example = "100", required = true)
    @NotNull(message = "Disk size is required")
    @Min(value = 1, message = "Disk size must be at least 1 GB")
    @Max(value = 65536, message = "Cannot exceed 64TB disk size")
    Integer sizeGB,
    
    @Schema(description = "Enable SSD emulation", example = "true")
    Boolean ssd,
    
    @Schema(description = "Enable IO thread (for better performance)", example = "true")
    Boolean iothread,
    
    @Schema(description = "Cache mode", example = "writeback")
    CacheMode cache,
    
    @Schema(description = "Enable discard/TRIM", example = "true")
    Boolean discard,
    
    @Schema(description = "Disk format", example = "qcow2")
    DiskFormat format,
    
    @Schema(description = "Enable backup for this disk", example = "true")
    Boolean backup,
    
    @Schema(description = "Enable replication for this disk", example = "false")
    Boolean replicate,
    
    @Schema(description = "Additional raw options", example = "aio=native")
    String additionalOptions
) {
    
    /**
     * Disk interface types
     */
    @Schema(enumeration = {"scsi", "virtio", "ide", "sata"})
    public enum DiskInterface {
        SCSI("scsi"),
        VIRTIO("virtio"),
        IDE("ide"),
        SATA("sata");
        
        private final String value;
        
        DiskInterface(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Cache modes
     */
    @Schema(enumeration = {"none", "writethrough", "writeback", "unsafe", "directsync"})
    public enum CacheMode {
        NONE("none"),
        WRITETHROUGH("writethrough"),
        WRITEBACK("writeback"),
        UNSAFE("unsafe"),
        DIRECTSYNC("directsync");
        
        private final String value;
        
        CacheMode(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Disk formats
     */
    @Schema(enumeration = {"raw", "qcow2", "vmdk"})
    public enum DiskFormat {
        RAW("raw"),
        QCOW2("qcow2"),
        VMDK("vmdk");
        
        private final String value;
        
        DiskFormat(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Build the disk configuration string for Proxmox API
     * Example: "local-lvm:100,ssd=1,iothread=1,cache=writeback"
     */
    public String toProxmoxString() {
        StringBuilder config = new StringBuilder();
        
        // Storage and size are required
        config.append(storage).append(":").append(sizeGB);
        
        // Add optional parameters
        if (ssd != null && ssd) {
            config.append(",ssd=1");
        }
        
        if (iothread != null && iothread) {
            config.append(",iothread=1");
        }
        
        if (cache != null) {
            config.append(",cache=").append(cache.getValue());
        }
        
        if (discard != null && discard) {
            config.append(",discard=on");
        }
        
        if (format != null) {
            config.append(",format=").append(format.getValue());
        }
        
        if (backup != null) {
            config.append(",backup=").append(backup ? "1" : "0");
        }
        
        if (replicate != null) {
            config.append(",replicate=").append(replicate ? "1" : "0");
        }
        
        if (additionalOptions != null && !additionalOptions.isBlank()) {
            config.append(",").append(additionalOptions);
        }
        
        return config.toString();
    }
    
    /**
     * Get the disk parameter name (e.g., "scsi0", "virtio1")
     */
    public String getParameterName() {
        return interfaceType.getValue() + slot;
    }
}