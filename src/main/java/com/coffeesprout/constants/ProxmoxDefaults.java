package com.coffeesprout.constants;

/**
 * Default values for Proxmox configuration.
 * Centralizes default settings to ensure consistency across the application.
 */
public final class ProxmoxDefaults {
    
    private ProxmoxDefaults() {
        // Prevent instantiation
    }
    
    /**
     * Hardware defaults
     */
    public static final String DEFAULT_SCSI_HW = "virtio-scsi-pci";
    public static final String DEFAULT_CPU_TYPE = "x86-64-v2-AES";
    public static final String DEFAULT_VGA_TYPE = "std";
    public static final String DEFAULT_BOOT_ORDER = "c";
    
    /**
     * Cloud-init defaults
     */
    public static final String DEFAULT_CI_USER = "ubuntu";
    public static final String DEFAULT_CI_STORAGE = "local-lvm";
    public static final String CI_DRIVE_NAME = "cloudinit";
    
    /**
     * Storage defaults
     */
    public static final String DEFAULT_STORAGE = "local-zfs";
    public static final String DEFAULT_BACKUP_STORAGE = "local";
    
    /**
     * Network defaults
     */
    public static final String DEFAULT_BRIDGE = "vmbr0";
    public static final String DEFAULT_NETWORK_MODEL = "virtio";
    
    /**
     * Snapshot defaults
     */
    public static final String SNAPSHOT_NAME_PATTERN = "auto-{vm}-{date}";
    public static final int DEFAULT_MAX_SNAPSHOTS = 7;
    
    /**
     * Resource defaults
     */
    public static final int DEFAULT_CORES = 2;
    public static final int DEFAULT_MEMORY_MB = 2048;
    public static final int DEFAULT_DISK_SIZE_GB = 20;
    
    /**
     * Timeout defaults (in seconds)
     */
    public static final int DEFAULT_TASK_TIMEOUT = 300;
    public static final int DEFAULT_MIGRATION_TIMEOUT = 600;
    public static final int DEFAULT_BACKUP_TIMEOUT = 3600;
}