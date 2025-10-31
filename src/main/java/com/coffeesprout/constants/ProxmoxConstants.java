package com.coffeesprout.constants;

import com.coffeesprout.util.UnitConverter;

/**
 * Constants specific to Proxmox API interactions.
 * These values are defined by the Proxmox VE API specification.
 */
public final class ProxmoxConstants {

    private ProxmoxConstants() {
        throw new AssertionError("ProxmoxConstants is a utility class and should not be instantiated");
    }

    /**
     * API paths and endpoints
     */
    public static final class API {
        public static final String BASE_PATH = "/api2/json";
        public static final String NODES_PATH = "/nodes";
        public static final String QEMU_PATH = "/qemu";
        public static final String LXC_PATH = "/lxc";
        public static final String STORAGE_PATH = "/storage";
        public static final String NETWORK_PATH = "/network";
        public static final String ACCESS_PATH = "/access";

        private API() {}
    }

    /**
     * Form field names for API requests
     */
    public static final class FormFields {
        // VM Creation fields
        public static final String VMID = "vmid";
        public static final String NAME = "name";
        public static final String CORES = "cores";
        public static final String MEMORY = "memory";
        public static final String SOCKETS = "sockets";
        public static final String CPU = "cpu";
        public static final String NUMA = "numa";
        public static final String OSTYPE = "ostype";
        public static final String BIOS = "bios";
        public static final String MACHINE = "machine";
        public static final String DESCRIPTION = "description";
        public static final String TAGS = "tags";
        public static final String ONBOOT = "onboot";
        public static final String AGENT = "agent";
        public static final String BOOT = "boot";
        public static final String BOOTDISK = "bootdisk";
        public static final String VGA = "vga";
        public static final String SERIAL = "serial0";

        // Migration fields
        public static final String TARGET = "target";
        public static final String ONLINE = "online";
        public static final String WITH_LOCAL_DISKS = "with-local-disks";
        public static final String FORCE = "force";
        public static final String MIGRATION_TYPE = "migration_type";
        public static final String MIGRATION_NETWORK = "migration_network";
        public static final String BWLIMIT = "bwlimit";
        public static final String TARGET_STORAGE = "targetstorage";

        // Snapshot fields
        public static final String SNAPNAME = "snapname";
        public static final String VMSTATE = "vmstate";

        // Backup fields
        public static final String COMPRESS = "compress";
        public static final String MODE = "mode";
        public static final String MAILTO = "mailto";
        public static final String MAILNOTIFICATION = "mailnotification";
        public static final String NOTES_TEMPLATE = "notes-template";

        // Cloud-init fields
        public static final String CIUSER = "ciuser";
        public static final String CIPASSWORD = "cipassword";
        public static final String SSHKEYS = "sshkeys";
        public static final String SEARCHDOMAIN = "searchdomain";
        public static final String NAMESERVER = "nameserver";
        public static final String IPCONFIG_PREFIX = "ipconfig";

        private FormFields() {}
    }

    /**
     * Header names for API requests
     */
    public static final class Headers {
        public static final String COOKIE = "Cookie";
        public static final String CSRF_TOKEN = "CSRFPreventionToken";
        public static final String AUTH_COOKIE = "PVEAuthCookie";
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
        public static final String APPLICATION_JSON = "application/json";

        private Headers() {}
    }

    /**
     * Response field names
     */
    public static final class ResponseFields {
        public static final String DATA = "data";
        public static final String ERRORS = "errors";
        public static final String STATUS = "status";
        public static final String MESSAGE = "message";
        public static final String SUCCESS = "success";
        public static final String UPID = "UPID";

        private ResponseFields() {}
    }

    /**
     * Task types as returned by Proxmox API
     */
    public static final class TaskTypes {
        public static final String QMSTART = "qmstart";
        public static final String QMSTOP = "qmstop";
        public static final String QMREBOOT = "qmreboot";
        public static final String QMSHUTDOWN = "qmshutdown";
        public static final String QMRESET = "qmreset";
        public static final String QMCLONE = "qmclone";
        public static final String QMMIGRATE = "qmmigrate";
        public static final String QMRESIZE = "qmresize";
        public static final String QMCREATE = "qmcreate";
        public static final String QMDESTROY = "qmdestroy";
        public static final String VZDUMP = "vzdump";
        public static final String VZRESTORE = "vzrestore";
        public static final String VNCPROXY = "vncproxy";
        public static final String SPICEPROXY = "spiceproxy";

        private TaskTypes() {}
    }

    /**
     * Storage types supported by Proxmox
     */
    public static final class StorageTypes {
        public static final String DIR = "dir";
        public static final String LVM = "lvm";
        public static final String LVM_THIN = "lvmthin";
        public static final String NFS = "nfs";
        public static final String CIFS = "cifs";
        public static final String GLUSTERFS = "glusterfs";
        public static final String ISCSI = "iscsi";
        public static final String CEPH = "rbd";
        public static final String CEPHFS = "cephfs";
        public static final String ZFS = "zfspool";
        public static final String ZFS_OVER_ISCSI = "zfs";
        public static final String PBS = "pbs"; // Proxmox Backup Server

        private StorageTypes() {}
    }

    /**
     * Network types
     */
    public static final class NetworkTypes {
        public static final String BRIDGE = "bridge";
        public static final String BOND = "bond";
        public static final String VLAN = "vlan";
        public static final String OVS_BRIDGE = "OVSBridge";
        public static final String OVS_BOND = "OVSBond";
        public static final String OVS_PORT = "OVSPort";
        public static final String OVS_INTPORT = "OVSIntPort";

        private NetworkTypes() {}
    }

    /**
     * Permission paths
     */
    public static final class Permissions {
        public static final String VM_ALLOCATE = "VM.Allocate";
        public static final String VM_CLONE = "VM.Clone";
        public static final String VM_CONFIG = "VM.Config";
        public static final String VM_MIGRATE = "VM.Migrate";
        public static final String VM_POWER_MGT = "VM.PowerMgmt";
        public static final String VM_BACKUP = "VM.Backup";
        public static final String VM_SNAPSHOT = "VM.Snapshot";
        public static final String VM_AUDIT = "VM.Audit";
        public static final String VM_CONSOLE = "VM.Console";
        public static final String VM_MONITOR = "VM.Monitor";
        public static final String DATASTORE_ALLOCATE = "Datastore.Allocate";
        public static final String DATASTORE_ALLOCATE_SPACE = "Datastore.AllocateSpace";
        public static final String DATASTORE_ALLOCATE_TEMPLATE = "Datastore.AllocateTemplate";
        public static final String DATASTORE_AUDIT = "Datastore.Audit";
        public static final String SYS_AUDIT = "Sys.Audit";
        public static final String SYS_MODIFY = "Sys.Modify";
        public static final String SYS_POWER_MGT = "Sys.PowerMgmt";

        private Permissions() {}
    }

    /**
     * Special values and limits
     */
    public static final class Limits {
        public static final int MAX_PARALLEL_OPERATIONS = 10;
        public static final int MAX_API_VERSION = 8;
        public static final int MIN_API_VERSION = 2;
        public static final long MAX_UPLOAD_SIZE = 128L * UnitConverter.Bytes.BYTES_PER_GB; // 128GB
        public static final int DEFAULT_CONSOLE_PORT = 5900; // VNC
        public static final int DEFAULT_SPICE_PORT = 3128;
        public static final int DEFAULT_API_PORT = 8006;

        private Limits() {}
    }
}
