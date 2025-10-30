package com.coffeesprout.constants;

/**
 * Constants for VM operations and status values.
 * Centralizes all VM-related constants to avoid magic strings throughout the codebase.
 */
public final class VMConstants {

    private VMConstants() {
        // Prevent instantiation
    }

    /**
     * VM Status values as returned by Proxmox API
     */
    public static final class Status {
        public static final String RUNNING = "running";
        public static final String STOPPED = "stopped";
        public static final String SUSPENDED = "suspended";
        public static final String PAUSED = "paused";
        public static final String HIBERNATED = "hibernated";

        private Status() {}
    }

    /**
     * VM Lock states
     */
    public static final class Lock {
        public static final String BACKUP = "backup";
        public static final String CLONE = "clone";
        public static final String CREATE = "create";
        public static final String MIGRATE = "migrate";
        public static final String ROLLBACK = "rollback";
        public static final String SNAPSHOT = "snapshot";
        public static final String SNAPSHOT_DELETE = "snapshot-delete";
        public static final String SUSPENDED = "suspended";
        public static final String SUSPENDING = "suspending";

        private Lock() {}
    }

    /**
     * Network interface patterns and defaults
     */
    public static final class Network {
        public static final String INTERFACE_PATTERN = "net\\d+";
        public static final String DEFAULT_MODEL = "virtio";
        public static final String DEFAULT_BRIDGE = "vmbr0";
        public static final int MAX_INTERFACES = 8;

        private Network() {}
    }

    /**
     * Disk interface types and limits
     */
    public static final class Disk {
        public static final String SCSI_PREFIX = "scsi";
        public static final String VIRTIO_PREFIX = "virtio";
        public static final String IDE_PREFIX = "ide";
        public static final String SATA_PREFIX = "sata";

        public static final int MAX_SCSI_DEVICES = 31;
        public static final int MAX_VIRTIO_DEVICES = 16;
        public static final int MAX_IDE_DEVICES = 4;
        public static final int MAX_SATA_DEVICES = 6;

        public static final String DEFAULT_FORMAT = "raw";
        public static final String DEFAULT_STORAGE = "local-zfs";
        public static final int MIN_SIZE_GB = 1;
        public static final int MAX_SIZE_GB = 131072; // 128TB

        private Disk() {}
    }

    /**
     * VM Resource limits and defaults
     */
    public static final class Resources {
        public static final int MIN_CORES = 1;
        public static final int MAX_CORES = 128;
        public static final int DEFAULT_CORES = 2;

        public static final int MIN_MEMORY_MB = 512;
        public static final int MAX_MEMORY_MB = 4194304; // 4TB
        public static final int DEFAULT_MEMORY_MB = 2048;

        public static final int MIN_VM_ID = 100;
        public static final int MAX_VM_ID = 999999999;

        public static final String DEFAULT_CPU_TYPE = "x86-64-v2-AES";
        public static final String DEFAULT_VGA_TYPE = "std";
        public static final String DEFAULT_BIOS_TYPE = "seabios";
        public static final String DEFAULT_OS_TYPE = "l26"; // Linux 2.6+
        public static final String DEFAULT_MACHINE_TYPE = "q35";

        private Resources() {}
    }

    /**
     * Pagination defaults
     */
    public static final class Pagination {
        public static final int DEFAULT_PAGE_SIZE = 100;
        public static final int MAX_PAGE_SIZE = 1000;
        public static final int MIN_PAGE_SIZE = 10;

        private Pagination() {}
    }

    /**
     * Timeout values in seconds
     */
    public static final class Timeouts {
        public static final int DEFAULT_START_TIMEOUT = 30;
        public static final int DEFAULT_STOP_TIMEOUT = 120;
        public static final int DEFAULT_REBOOT_TIMEOUT = 150;
        public static final int MIGRATION_POLL_INTERVAL = 2;
        public static final int TASK_POLL_INTERVAL = 2;
        public static final int SNAPSHOT_TIMEOUT = 60;
        public static final int BACKUP_TIMEOUT = 3600; // 1 hour

        private Timeouts() {}
    }

    /**
     * Snapshot configuration
     */
    public static final class Snapshot {
        public static final int MAX_SNAPSHOTS_PER_VM = 100;
        public static final int MAX_NAME_LENGTH = 40;
        public static final int MAX_DESCRIPTION_LENGTH = 255;
        public static final String DEFAULT_PREFIX = "snap";

        private Snapshot() {}
    }

    /**
     * Backup configuration
     */
    public static final class Backup {
        public static final String DEFAULT_MODE = "snapshot";
        public static final String DEFAULT_COMPRESSION = "zstd";
        public static final int DEFAULT_RETENTION_DAYS = 7;
        public static final int MAX_CONCURRENT_BACKUPS = 5;

        private Backup() {}
    }

    /**
     * Tag configuration
     */
    public static final class Tags {
        public static final int MAX_TAGS_PER_VM = 64;
        public static final int MAX_TAG_LENGTH = 64;
        public static final String SEPARATOR = ";"; // Proxmox uses semicolon
        public static final String PREFIX_SEPARATOR = "-"; // Use dash instead of colon
        public static final String MOXXIE_TAG = "moxxie";

        private Tags() {}
    }

    /**
     * Cloud-init configuration
     */
    public static final class CloudInit {
        public static final String DEFAULT_DRIVE = "ide2";
        public static final String FORMAT = "nocloud";
        public static final String MEDIA = "cdrom";
        public static final String DNS_DOMAIN = "localdomain";

        private CloudInit() {}
    }

    /**
     * Task status values
     */
    public static final class TaskStatus {
        public static final String OK = "OK";
        public static final String RUNNING = "running";
        public static final String STOPPED = "stopped";

        private TaskStatus() {}
    }
}
