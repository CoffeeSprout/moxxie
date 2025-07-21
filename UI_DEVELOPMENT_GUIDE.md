# Moxxie UI Development Guide - Screens & Data

This guide defines the screens needed for the Moxxie UI, their data requirements, and provides real API examples with response data.

## Table of Contents

1. [Dashboard](#dashboard)
2. [VM List & Management](#vm-list--management)
3. [VM Detail](#vm-detail)
4. [Snapshot Management](#snapshot-management)
5. [Backup Management](#backup-management)
6. [Scheduler & Jobs](#scheduler--jobs)
7. [Storage Overview](#storage-overview)
8. [Network Management](#network-management)
9. [Node Management](#node-management)
10. [Migration Wizard](#migration-wizard)
11. [Bulk Operations](#bulk-operations)
12. [Tag Management](#tag-management)
13. [Console Access](#console-access)
14. [Audit & Activity Log](#audit--activity-log)
15. [Cluster Provisioning](#cluster-provisioning)
16. [Talos Cluster Management](#talos-cluster-management)

## Dashboard

### Purpose
Provide a high-level overview of cluster health, resource usage, and recent activity.

### Data Requirements
- Cluster status and node count
- Total resource usage (CPU, Memory, Storage)
- VM count by status
- Recent tasks
- Active alerts/issues
- Storage health
- Backup status summary

### API Calls & Example Data

**Get Cluster Overview:**
```bash
curl -X GET http://localhost:8080/api/v1/cluster/status
```

Response:
```json
{
  "cluster": {
    "name": "production-cluster",
    "version": 8,
    "nodes": 3,
    "quorate": true
  },
  "nodes": [
    {
      "name": "proxmox-01",
      "type": "node",
      "online": true,
      "level": "",
      "id": "node/proxmox-01",
      "ip": "192.168.1.10",
      "local": false
    },
    {
      "name": "proxmox-02",
      "type": "node",
      "online": true,
      "level": "",
      "id": "node/proxmox-02",
      "ip": "192.168.1.11",
      "local": false
    },
    {
      "name": "proxmox-03",
      "type": "node",
      "online": true,
      "level": "",
      "id": "node/proxmox-03",
      "ip": "192.168.1.12",
      "local": true
    }
  ]
}
```

**Get Resource Summary:**
```bash
curl -X GET http://localhost:8080/api/v1/cluster/resources?type=vm
```

Response:
```json
[
  {
    "id": "qemu/101",
    "type": "qemu",
    "node": "proxmox-01",
    "status": "running",
    "name": "web-server-01",
    "cpu": 0.05234,
    "maxcpu": 4,
    "mem": 3221225472,
    "maxmem": 4294967296,
    "disk": 0,
    "maxdisk": 53687091200,
    "uptime": 864000,
    "template": false
  },
  {
    "id": "qemu/102",
    "type": "qemu",
    "node": "proxmox-01",
    "status": "stopped",
    "name": "database-01",
    "cpu": 0,
    "maxcpu": 8,
    "mem": 0,
    "maxmem": 17179869184,
    "disk": 0,
    "maxdisk": 107374182400,
    "uptime": 0,
    "template": false
  }
]
```

**Get Recent Tasks:**
```bash
curl -X GET "http://localhost:8080/api/v1/tasks?limit=5&statusfilter=running,error"
```

Response:
```json
[
  {
    "upid": "UPID:proxmox-01:00001234:00ABCDEF:67890ABC:qmcreate:103:root@pam:",
    "node": "proxmox-01",
    "pid": 4660,
    "pstart": 11534591,
    "starttime": 1719230400,
    "type": "qmcreate",
    "id": "103",
    "user": "root@pam",
    "status": "running"
  },
  {
    "upid": "UPID:proxmox-02:00005678:00FEDCBA:67890DEF:qmbackup:105:root@pam:",
    "node": "proxmox-02",
    "pid": 22104,
    "pstart": 11534592,
    "starttime": 1719230500,
    "type": "qmbackup",
    "id": "105",
    "user": "root@pam",
    "status": "running"
  }
]
```

### Key Display Elements
- **Cluster Health Widget**: Show cluster name, quorum status, node count
- **Resource Gauges**: CPU, Memory, Storage usage as circular progress
- **VM Status Cards**: Running, Stopped, Templates count
- **Recent Activity Feed**: Last 5-10 tasks with status indicators
- **Quick Actions**: Create VM, Bulk Start/Stop buttons
- **Alerts Panel**: Any nodes offline, storage warnings, failed backups

## VM List & Management

### Purpose
Primary interface for viewing and managing all VMs in the cluster.

### Data Requirements
- VM list with filtering and sorting
- Resource usage per VM
- Tags for each VM
- Quick actions per VM
- Bulk selection

### API Calls & Example Data

**List VMs with Filters:**
```bash
curl -X GET "http://localhost:8080/api/v1/vms?tags=env-prod&status=running&limit=20"
```

Response:
```json
[
  {
    "vmid": 101,
    "name": "web-server-01",
    "node": "proxmox-01",
    "type": "qemu",
    "status": "running",
    "cpu": 0.15234,
    "cpus": 4,
    "mem": 3758096384,
    "maxmem": 4294967296,
    "disk": 21474836480,
    "maxdisk": 53687091200,
    "netin": 1234567890,
    "netout": 987654321,
    "diskread": 5368709120,
    "diskwrite": 2147483648,
    "uptime": 432000,
    "template": false,
    "tags": ["env-prod", "client-acme", "web-tier"],
    "lock": null,
    "hastate": null
  },
  {
    "vmid": 104,
    "name": "web-server-02",
    "node": "proxmox-02",
    "type": "qemu",
    "status": "running",
    "cpu": 0.08765,
    "cpus": 4,
    "mem": 4026531840,
    "maxmem": 4294967296,
    "disk": 19327352832,
    "maxdisk": 53687091200,
    "netin": 2345678901,
    "netout": 1876543210,
    "diskread": 4294967296,
    "diskwrite": 3221225472,
    "uptime": 345600,
    "template": false,
    "tags": ["env-prod", "client-acme", "web-tier", "always-on"],
    "lock": null,
    "hastate": null
  }
]
```

**Get Available Tags for Filtering:**
```bash
curl -X GET http://localhost:8080/api/v1/tags
```

Response:
```json
[
  {
    "name": "env-prod",
    "count": 15,
    "category": "environment"
  },
  {
    "name": "env-dev",
    "count": 8,
    "category": "environment"
  },
  {
    "name": "client-acme",
    "count": 12,
    "category": "client"
  },
  {
    "name": "always-on",
    "count": 5,
    "category": "criticality"
  },
  {
    "name": "web-tier",
    "count": 6,
    "category": "tier"
  }
]
```

### Key Display Elements
- **Filter Bar**: 
  - Status dropdown (All, Running, Stopped, Paused)
  - Tag selector with autocomplete
  - Node selector
  - Search by name
- **VM Table Columns**:
  - Selection checkbox
  - Status indicator (color-coded)
  - VM ID
  - Name (clickable to detail view)
  - Node
  - CPU usage (% and graph)
  - Memory usage (GB and progress bar)
  - Disk usage (GB and progress bar)
  - Network I/O rates
  - Uptime
  - Tags (color-coded chips)
  - Actions menu (Start/Stop/Restart/Console/Migrate)
- **Bulk Actions Bar**: Appears when VMs selected
- **Pagination Controls**: Items per page, current page

## VM Detail

### Purpose
Detailed view and management of a single VM.

### Data Requirements
- VM configuration details
- Current resource usage
- Network interfaces
- Disk configuration
- Snapshots list
- Recent backups
- Recent tasks for this VM
- Console access

### API Calls & Example Data

**Get VM Details with Config:**
```bash
curl -X GET http://localhost:8080/api/v1/vms/101/detail
```

Response:
```json
{
  "vm": {
    "vmid": 101,
    "name": "web-server-01",
    "node": "proxmox-01",
    "type": "qemu",
    "status": "running",
    "cpu": 0.15234,
    "cpus": 4,
    "mem": 3758096384,
    "maxmem": 4294967296,
    "disk": 21474836480,
    "maxdisk": 53687091200,
    "uptime": 432000,
    "tags": ["env-prod", "client-acme", "web-tier"]
  },
  "config": {
    "name": "web-server-01",
    "cores": 4,
    "memory": 4096,
    "boot": "order=scsi0;ide2;net0",
    "scsihw": "virtio-scsi-single",
    "ostype": "l26",
    "ide2": "none,media=cdrom",
    "net0": "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,firewall=1,tag=100",
    "net1": "virtio=AA:BB:CC:DD:EE:00,bridge=vmbr1",
    "scsi0": "local-zfs:vm-101-disk-0,size=50G",
    "scsi1": "local-zfs:vm-101-disk-1,size=100G",
    "cpu": "x86-64-v2-AES",
    "numa": 0,
    "balloon": 2048,
    "agent": 1,
    "onboot": 1,
    "protection": 0
  },
  "snapshots": [
    {
      "name": "before-update-20240620",
      "snaptime": 1718841600,
      "description": "Snapshot before system update",
      "parent": "current",
      "vmstate": false
    },
    {
      "name": "weekly-auto-20240616",
      "snaptime": 1718496000,
      "description": "Weekly automated snapshot",
      "parent": "before-update-20240620",
      "vmstate": false
    }
  ],
  "backups": [
    {
      "volid": "backup-nas:backup/vzdump-qemu-101-2024_06_21-02_00_00.vma.zst",
      "content": "backup",
      "ctime": 1718935200,
      "format": "vma.zst",
      "size": 4294967296,
      "notes": "Daily backup - env-prod",
      "protected": false,
      "verification": {
        "state": "ok",
        "upid": "UPID:proxmox-01:00001234:00ABCDEF:67890ABC:verifybackup:101:root@pam:"
      }
    }
  ]
}
```

### Key Display Elements
- **Header Section**:
  - VM name and ID
  - Status with color indicator
  - Power controls (Start/Stop/Shutdown/Reboot/Reset)
  - Quick actions (Console, Migrate, Clone, Remove)
- **Resource Usage Cards**:
  - CPU: Current usage %, cores allocated, real-time graph
  - Memory: Used/Total GB, percentage bar, balloon info
  - Disk: Used/Total per disk, I/O rates
  - Network: Interface list, IP addresses, I/O rates per interface
- **Configuration Tabs**:
  - **General**: OS type, boot order, CPU type, protection status
  - **Hardware**: Disks, network interfaces, display, other devices
  - **Options**: Start at boot, QEMU agent, balloon device
  - **Snapshots**: List with create/delete/rollback actions
  - **Backups**: Recent backups with restore option
  - **Tasks**: Recent operations on this VM
  - **Console**: Embedded console or launch button

## Snapshot Management

### Purpose
Manage VM snapshots including creation, deletion, and rollback.

### Data Requirements
- Snapshot tree/list for VM
- Snapshot metadata
- Available actions per snapshot
- Disk space impact

### API Calls & Example Data

**Create Snapshot:**
```bash
curl -X POST http://localhost:8080/api/v1/vms/101/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "before-upgrade-20240621",
    "description": "Snapshot before application upgrade",
    "includeVMState": false,
    "ttlHours": 168
  }'
```

Response:
```json
{
  "task": "UPID:proxmox-01:00003456:00CDEFAB:67890CDE:qmsnapshot:101:root@pam:",
  "message": "Snapshot creation started"
}
```

**List Snapshots with Hierarchy:**
```bash
curl -X GET http://localhost:8080/api/v1/vms/101/snapshots
```

Response:
```json
[
  {
    "name": "current",
    "digest": "1234567890abcdef",
    "running": true,
    "description": "You are here"
  },
  {
    "name": "before-upgrade-20240621",
    "snaptime": 1719230400,
    "description": "Snapshot before application upgrade (TTL: 168h)",
    "parent": "weekly-auto-20240616",
    "vmstate": false,
    "running": false
  },
  {
    "name": "weekly-auto-20240616",
    "snaptime": 1718841600,
    "description": "Weekly automated snapshot",
    "parent": "initial-setup",
    "vmstate": false,
    "running": false
  },
  {
    "name": "initial-setup",
    "snaptime": 1718236800,
    "description": "Initial VM setup complete",
    "parent": null,
    "vmstate": false,
    "running": false
  }
]
```

### Key Display Elements
- **Snapshot Tree View**:
  - Visual hierarchy showing parent-child relationships
  - Current position marker
  - Snapshot names with timestamps
  - TTL indicators for expiring snapshots
- **Snapshot Actions**:
  - Create new snapshot button
  - Per-snapshot: Rollback, Delete, Clone from snapshot
- **Snapshot Details Panel** (when selected):
  - Creation time
  - Description
  - Include RAM state indicator
  - Size on disk
  - TTL remaining (if set)
- **Bulk Snapshot Section**:
  - Create snapshots for multiple VMs
  - Pattern-based naming
  - TTL settings

## Backup Management

### Purpose
Manage VM backups, retention policies, and restoration.

### Data Requirements
- Backup list per VM or globally
- Storage location info
- Backup metadata and verification status
- Retention policy settings
- Restore capabilities

### API Calls & Example Data

**List All Backups:**
```bash
curl -X GET "http://localhost:8080/api/v1/backups?storage=backup-nas&vmId=101"
```

Response:
```json
[
  {
    "volid": "backup-nas:backup/vzdump-qemu-101-2024_06_21-02_00_00.vma.zst",
    "content": "backup",
    "ctime": 1719230400,
    "format": "vma.zst",
    "size": 5368709120,
    "vmid": 101,
    "notes": "Daily backup - env-prod\nRetention: 7 days\nTags: env-prod, client-acme",
    "protected": false,
    "verification": {
      "state": "ok",
      "upid": "UPID:proxmox-01:00001234:00ABCDEF:67890ABC:verifybackup:101:root@pam:",
      "lastVerified": 1719234000
    }
  },
  {
    "volid": "backup-nas:backup/vzdump-qemu-101-2024_06_20-02_00_00.vma.zst",
    "content": "backup",
    "ctime": 1719144000,
    "format": "vma.zst",
    "size": 5368709120,
    "vmid": 101,
    "notes": "Daily backup - env-prod",
    "protected": true,
    "verification": {
      "state": "ok",
      "upid": "UPID:proxmox-01:00001235:00ABCDF0:67890ABD:verifybackup:101:root@pam:",
      "lastVerified": 1719147600
    }
  }
]
```

**Get Backup Configuration:**
```bash
curl -X GET "http://localhost:8080/api/v1/backups/backup-nas/vzdump-qemu-101-2024_06_21-02_00_00.vma.zst/config"
```

Response:
```json
{
  "name": "web-server-01",
  "bootdisk": "scsi0",
  "cores": 4,
  "memory": 4096,
  "net0": "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0,firewall=1,tag=100",
  "scsi0": "local-zfs:vm-101-disk-0,size=50G",
  "ostype": "l26",
  "scsihw": "virtio-scsi-single"
}
```

### Key Display Elements
- **Backup List View**:
  - Storage selector dropdown
  - VM filter
  - Table with: VM ID/Name, Backup Time, Size, Format, Verification Status, Protected flag
  - Actions: Restore, Delete, Protect/Unprotect, Verify, Download
- **Backup Details** (when selected):
  - Full configuration at time of backup
  - Notes and metadata
  - Verification history
  - File path and storage location
- **Retention Policy Panel**:
  - Keep last N backups
  - Keep daily/weekly/monthly settings
  - Protected backup count
- **Restore Wizard**:
  - Target VM selection (same or new ID)
  - Storage mapping for disks
  - Network mapping
  - Start after restore option

## Scheduler & Jobs

### Purpose
Manage scheduled tasks for automated operations.

### Data Requirements
- Job list with schedules
- Job execution history
- VM selection criteria
- Job parameters
- Next run times

### API Calls & Example Data

**List Scheduled Jobs:**
```bash
curl -X GET http://localhost:8080/api/v1/scheduler/jobs
```

Response:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "daily-prod-snapshots",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "createdAt": "2024-06-01T10:00:00Z",
    "lastRun": "2024-06-21T02:00:00Z",
    "lastRunStatus": "SUCCESS",
    "nextRun": "2024-06-22T02:00:00Z",
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "description": "Automated daily snapshot",
      "maxSnapshots": "7",
      "includeVMState": "false"
    },
    "vmSelectors": [
      {
        "type": "TAG_EXPRESSION",
        "value": "env-prod AND NOT always-on"
      }
    ],
    "statistics": {
      "totalRuns": 20,
      "successfulRuns": 19,
      "failedRuns": 1,
      "averageDuration": 45.5
    }
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "name": "cleanup-dev-snapshots",
    "taskType": "snapshot_delete",
    "cronExpression": "0 0 4 * * ?",
    "enabled": true,
    "createdAt": "2024-06-01T10:00:00Z",
    "lastRun": "2024-06-21T04:00:00Z",
    "lastRunStatus": "SUCCESS",
    "nextRun": "2024-06-22T04:00:00Z",
    "parameters": {
      "ageThresholdHours": "168",
      "namePattern": "auto-*",
      "checkDescription": "true",
      "safeMode": "true"
    },
    "vmSelectors": [
      {
        "type": "TAG_EXPRESSION",
        "value": "env-dev"
      }
    ],
    "statistics": {
      "totalRuns": 20,
      "successfulRuns": 20,
      "failedRuns": 0,
      "averageDuration": 12.3
    }
  }
]
```

**Get Job Execution History:**
```bash
curl -X GET "http://localhost:8080/api/v1/scheduler/jobs/550e8400-e29b-41d4-a716-446655440000/history?limit=5"
```

Response:
```json
[
  {
    "executionId": "exec-12345",
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "startTime": "2024-06-21T02:00:00Z",
    "endTime": "2024-06-21T02:00:45Z",
    "status": "SUCCESS",
    "affectedVMs": [101, 102, 104, 105, 107],
    "results": {
      "total": 5,
      "successful": 5,
      "failed": 0,
      "details": [
        {"vmId": 101, "status": "SUCCESS", "message": "Snapshot created: auto-web-server-01-20240621"},
        {"vmId": 102, "status": "SUCCESS", "message": "Snapshot created: auto-database-01-20240621"}
      ]
    }
  }
]
```

### Key Display Elements
- **Job List Table**:
  - Status indicator (enabled/disabled)
  - Job name
  - Task type with icon
  - Schedule (human-readable)
  - Last run status with timestamp
  - Next run time
  - Quick actions (Run now, Edit, Disable, Delete)
- **Job Creation/Edit Form**:
  - Name and description
  - Task type selector
  - Cron expression builder with presets
  - VM selector builder (tags, IDs, patterns)
  - Task-specific parameters
  - Test/Preview affected VMs
- **Execution History Panel**:
  - Timeline view of executions
  - Success/failure indicators
  - Duration and affected VM count
  - Detailed results on expansion

## Storage Overview

### Purpose
Monitor and manage storage resources across the cluster.

### Data Requirements
- Storage list with types and usage
- Content types supported
- Which nodes can access
- Usage trends

### API Calls & Example Data

**List Storage Resources:**
```bash
curl -X GET "http://localhost:8080/api/v1/storage?enabled=true"
```

Response:
```json
[
  {
    "storage": "local",
    "type": "dir",
    "content": ["vztmpl", "iso", "backup"],
    "active": true,
    "enabled": true,
    "shared": false,
    "total": 107374182400,
    "used": 64424509440,
    "available": 42949672960,
    "used_fraction": 0.60,
    "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
    "path": "/var/lib/vz"
  },
  {
    "storage": "local-zfs",
    "type": "zfspool",
    "content": ["images", "rootdir"],
    "active": true,
    "enabled": true,
    "shared": false,
    "total": 536870912000,
    "used": 322122547200,
    "available": 214748364800,
    "used_fraction": 0.60,
    "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
    "pool": "rpool/data"
  },
  {
    "storage": "backup-nas",
    "type": "nfs",
    "content": ["backup", "vztmpl", "iso"],
    "active": true,
    "enabled": true,
    "shared": true,
    "total": 10995116277760,
    "used": 6597069766656,
    "available": 4398046511104,
    "used_fraction": 0.60,
    "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
    "server": "192.168.1.50",
    "export": "/volume1/proxmox-backups"
  }
]
```

### Key Display Elements
- **Storage Cards Grid**:
  - Storage name and type icon
  - Usage gauge (visual)
  - Total/Used/Available space
  - Content types as chips
  - Shared/Local indicator
  - Node availability
- **Storage Details** (on selection):
  - Configuration details
  - Performance metrics if available
  - Content browser (ISOs, templates)
  - Recent activity
- **Actions Available**:
  - Upload ISO/Template
  - Browse contents
  - Edit configuration
  - Enable/Disable

## Network Management

### Purpose
View and manage network configuration including bridges, VLANs, and SDN.

### Data Requirements
- Network interfaces per node
- Bridge configurations
- VLAN assignments
- SDN zones and vnets
- Active connections

### API Calls & Example Data

**List Network Interfaces:**
```bash
curl -X GET "http://localhost:8080/api/v1/networks?node=proxmox-01"
```

Response:
```json
[
  {
    "iface": "vmbr0",
    "type": "bridge",
    "active": true,
    "autostart": true,
    "bridge_ports": "enp0s3",
    "bridge_stp": "off",
    "bridge_fd": 0,
    "address": "192.168.1.10",
    "netmask": "255.255.255.0",
    "gateway": "192.168.1.1",
    "families": ["inet"],
    "method": "static",
    "comments": "Management Network"
  },
  {
    "iface": "vmbr1",
    "type": "bridge",
    "active": true,
    "autostart": true,
    "bridge_ports": "enp0s8",
    "bridge_stp": "off",
    "bridge_fd": 0,
    "bridge_vlan_aware": true,
    "families": ["inet"],
    "method": "manual",
    "comments": "VM Network - VLAN Aware"
  },
  {
    "iface": "enp0s3",
    "type": "eth",
    "active": true,
    "autostart": true,
    "method": "manual",
    "families": ["inet"]
  }
]
```

**List SDN VNets:**
```bash
curl -X GET http://localhost:8080/api/v1/sdn/vnets
```

Response:
```json
[
  {
    "vnet": "vnet100",
    "zone": "zone-datacenter",
    "type": "vnet",
    "tag": 100,
    "alias": "Production Network",
    "ipam": "pve",
    "mac": "BC:24:11:00:00:64"
  },
  {
    "vnet": "vnet200",
    "zone": "zone-datacenter",
    "type": "vnet",
    "tag": 200,
    "alias": "Development Network",
    "ipam": "pve",
    "mac": "BC:24:11:00:00:C8"
  }
]
```

### Key Display Elements
- **Network Topology View**:
  - Visual representation of bridges and connections
  - VLAN tags on bridges
  - Physical interface mappings
- **Interface List**:
  - Interface name and type
  - IP configuration
  - Active/Inactive status
  - VLAN awareness indicator
  - Comments/description
- **SDN Section**:
  - Zone list with type
  - VNet configuration
  - Subnet assignments
  - IPAM status

## Node Management

### Purpose
Monitor node health and manage node-specific settings.

### Data Requirements
- Node status and resources
- Running services
- Storage availability
- Network configuration
- Cluster membership

### API Calls & Example Data

**Get Node Status:**
```bash
curl -X GET http://localhost:8080/api/v1/nodes/proxmox-01
```

Response:
```json
{
  "node": "proxmox-01",
  "status": "online",
  "cpu": 0.12,
  "maxcpu": 32,
  "mem": 25769803776,
  "maxmem": 137438953472,
  "disk": 64424509440,
  "maxdisk": 107374182400,
  "uptime": 8640000,
  "pveversion": "pve-manager/8.0.3/bbf3993334bfa916",
  "kversion": "Linux 6.2.16-3-pve #1 SMP PREEMPT_DYNAMIC PVE 6.2.16-3",
  "loadavg": [0.15, 0.20, 0.18],
  "cpuinfo": {
    "model": "Intel(R) Xeon(R) E-2288G CPU @ 3.70GHz",
    "sockets": 1,
    "cores": 8,
    "threads": 16,
    "mhz": 3700
  },
  "rootfs": {
    "total": 107374182400,
    "used": 64424509440,
    "free": 42949672960
  },
  "swap": {
    "total": 8589934592,
    "used": 1073741824,
    "free": 7516192768
  }
}
```

**Get Node Services:**
```bash
curl -X GET http://localhost:8080/api/v1/nodes/proxmox-01/services
```

Response:
```json
[
  {
    "name": "pve-cluster",
    "desc": "The Proxmox VE cluster filesystem",
    "state": "running"
  },
  {
    "name": "pvedaemon",
    "desc": "PVE API Daemon",
    "state": "running"
  },
  {
    "name": "pve-firewall",
    "desc": "Proxmox VE firewall",
    "state": "running"
  },
  {
    "name": "pveproxy",
    "desc": "PVE API Proxy Server",
    "state": "running"
  }
]
```

### Key Display Elements
- **Node Cards** (for each node):
  - Online/Offline status
  - CPU usage gauge
  - Memory usage gauge
  - Storage usage
  - Uptime
  - Version info
- **Node Detail View**:
  - System information (kernel, PVE version)
  - CPU details (model, cores, frequency)
  - Memory breakdown (used, cached, free)
  - Load average graph
  - Service status list
  - Recent tasks on node
- **Actions**:
  - Reboot/Shutdown node
  - Service management
  - Shell access
  - Update management

## Migration Wizard

### Purpose
Guide users through VM migration between nodes.

### Data Requirements
- Source and target nodes
- VM eligibility for migration
- Storage compatibility
- Network availability
- Migration options

### API Calls & Example Data

**Check Migration Feasibility:**
```bash
curl -X GET "http://localhost:8080/api/v1/vms/101/migrate/check?target=proxmox-02"
```

Response:
```json
{
  "allowed": true,
  "running": true,
  "localDisks": [
    {
      "volid": "local-zfs:vm-101-disk-0",
      "size": 53687091200,
      "replicatable": false,
      "targetStorage": ["local-zfs", "shared-storage"]
    }
  ],
  "warnings": [
    "VM has local disks that will need to be migrated",
    "Online migration will cause brief network interruption"
  ],
  "targetStorages": [
    {
      "storage": "local-zfs",
      "available": 214748364800,
      "type": "zfspool"
    },
    {
      "storage": "shared-storage",
      "available": 1099511627776,
      "type": "nfs"
    }
  ]
}
```

**Start Migration:**
```bash
curl -X POST http://localhost:8080/api/v1/vms/101/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "targetNode": "proxmox-02",
    "online": true,
    "withLocalDisks": true,
    "targetStorage": {
      "local-zfs:vm-101-disk-0": "local-zfs"
    },
    "force": false
  }'
```

Response:
```json
{
  "task": "UPID:proxmox-01:00009876:00EFCDAB:67890EDF:qmigrate:101:root@pam:",
  "message": "Migration started"
}
```

**Monitor Migration Progress:**
```bash
curl -X GET http://localhost:8080/api/v1/vms/101/migrate/status
```

Response:
```json
{
  "running": true,
  "progress": 0.65,
  "status": "drive-scsi0: Transferred 34.9 GiB of 50.0 GiB (65%)",
  "startTime": "2024-06-21T14:30:00Z",
  "estimatedCompletion": "2024-06-21T14:45:00Z",
  "speed": "125 MiB/s",
  "downtime": 0
}
```

### Key Display Elements
- **Migration Wizard Steps**:
  1. **VM Selection**: Choose VM(s) to migrate
  2. **Target Selection**: Choose destination node
  3. **Storage Mapping**: Map local disks to target storage
  4. **Options**: Online/offline, force, migration network
  5. **Review**: Summary of migration plan
  6. **Execute**: Start with progress monitoring
- **Progress View**:
  - Overall progress bar
  - Current operation status
  - Transfer speed
  - Estimated time remaining
  - Abort button
- **Post-Migration**:
  - Success/failure status
  - New location confirmation
  - Option to migrate more VMs

## Bulk Operations

### Purpose
Perform operations on multiple VMs simultaneously.

### Data Requirements
- VM selection criteria
- Operation parameters
- Dry run capability
- Progress tracking
- Per-VM results

### API Calls & Example Data

**Bulk Power Operation:**
```bash
curl -X POST http://localhost:8080/api/v1/bulk/power/stop \
  -H "Content-Type: application/json" \
  -d '{
    "vmIds": [101, 102, 104, 105],
    "force": false,
    "timeout": 30,
    "parallel": true,
    "dryRun": false
  }'
```

Response:
```json
{
  "operationId": "bulk-op-12345",
  "totalTargets": 4,
  "successful": 3,
  "failed": 1,
  "inProgress": 0,
  "results": [
    {
      "vmId": 101,
      "vmName": "web-server-01",
      "success": true,
      "message": "VM stopped successfully",
      "task": "UPID:proxmox-01:00001234:00ABCDEF:67890ABC:qmstop:101:root@pam:"
    },
    {
      "vmId": 102,
      "vmName": "database-01",
      "success": true,
      "message": "VM stopped successfully",
      "task": "UPID:proxmox-01:00001235:00ABCDF0:67890ABD:qmstop:102:root@pam:"
    },
    {
      "vmId": 104,
      "vmName": "web-server-02",
      "success": false,
      "error": "VM is locked (backup)",
      "message": "Cannot stop VM while backup is running"
    },
    {
      "vmId": 105,
      "vmName": "app-server-01",
      "success": true,
      "message": "VM stopped successfully",
      "task": "UPID:proxmox-02:00001236:00ABCDF1:67890ABE:qmstop:105:root@pam:"
    }
  ],
  "summary": {
    "duration": 15.7,
    "startTime": "2024-06-21T15:00:00Z",
    "endTime": "2024-06-21T15:00:15.7Z"
  }
}
```

**Bulk Snapshot Creation:**
```bash
curl -X POST http://localhost:8080/api/v1/bulk/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "vmIds": [101, 102, 105],
    "snapshotName": "pre-maint-{date}",
    "description": "Pre-maintenance snapshot",
    "ttlHours": 48,
    "includeVMState": false,
    "dryRun": true
  }'
```

Dry Run Response:
```json
{
  "dryRun": true,
  "affectedVMs": [
    {
      "vmId": 101,
      "vmName": "web-server-01",
      "currentSnapshots": 2,
      "proposedName": "pre-maint-20240621",
      "wouldSucceed": true
    },
    {
      "vmId": 102,
      "vmName": "database-01",
      "currentSnapshots": 5,
      "proposedName": "pre-maint-20240621",
      "wouldSucceed": true
    },
    {
      "vmId": 105,
      "vmName": "app-server-01",
      "currentSnapshots": 1,
      "proposedName": "pre-maint-20240621",
      "wouldSucceed": true
    }
  ],
  "estimatedDuration": 45,
  "warnings": []
}
```

### Key Display Elements
- **Operation Selection**:
  - Operation type buttons (Power, Snapshot, Backup, Tag)
  - VM selection method (manual, tags, filter)
  - Selected VM count with list preview
- **Parameter Configuration**:
  - Operation-specific options
  - Parallel vs sequential execution
  - Timeout settings
  - Dry run toggle
- **Execution View**:
  - Overall progress bar
  - Per-VM status indicators
  - Real-time log feed
  - Abort capability
- **Results Summary**:
  - Success/failure counts
  - Detailed results table
  - Export results option
  - Retry failed operations

## Tag Management

### Purpose
Organize VMs with tags and perform tag-based operations.

### Data Requirements
- Available tags with categories
- VMs per tag
- Tag operations (add, remove, rename)
- Tag-based filtering

### API Calls & Example Data

**Get Tag Summary:**
```bash
curl -X GET http://localhost:8080/api/v1/tags
```

Response:
```json
[
  {
    "name": "env-prod",
    "count": 15,
    "category": "environment",
    "color": "#d32f2f"
  },
  {
    "name": "env-dev",
    "count": 8,
    "category": "environment",
    "color": "#388e3c"
  },
  {
    "name": "client-acme",
    "count": 12,
    "category": "client",
    "color": "#1976d2"
  },
  {
    "name": "client-globex",
    "count": 7,
    "category": "client",
    "color": "#1976d2"
  },
  {
    "name": "always-on",
    "count": 5,
    "category": "criticality",
    "color": "#d32f2f"
  },
  {
    "name": "k8s-worker",
    "count": 6,
    "category": "kubernetes",
    "color": "#7b1fa2"
  }
]
```

**Bulk Tag Operation:**
```bash
curl -X POST "http://localhost:8080/api/v1/tags/bulk?namePattern=web-*" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD",
    "tags": ["web-tier", "public-facing"]
  }'
```

Response:
```json
{
  "totalVMs": 6,
  "modified": 6,
  "results": [
    {
      "vmId": 101,
      "vmName": "web-server-01",
      "previousTags": ["env-prod", "client-acme"],
      "currentTags": ["env-prod", "client-acme", "web-tier", "public-facing"],
      "success": true
    },
    {
      "vmId": 104,
      "vmName": "web-server-02",
      "previousTags": ["env-prod", "client-acme", "always-on"],
      "currentTags": ["env-prod", "client-acme", "always-on", "web-tier", "public-facing"],
      "success": true
    }
  ]
}
```

### Key Display Elements
- **Tag Cloud/List View**:
  - Tag name with color coding
  - VM count per tag
  - Category grouping
  - Search/filter tags
- **Tag Details** (on selection):
  - List of VMs with this tag
  - Quick actions for tagged VMs
  - Tag statistics
  - Related tags
- **Tag Operations**:
  - Create new tag with category/color
  - Bulk add/remove tags
  - Rename tag (updates all VMs)
  - Delete tag (with confirmation)
- **Tag Builder**:
  - Visual expression builder
  - AND/OR/NOT operations
  - Preview affected VMs
  - Save tag expressions

## Console Access

### Purpose
Provide console access to VMs through the web interface.

### Data Requirements
- Console URL generation
- Authentication ticket
- Console type preference
- Connection parameters

### API Calls & Example Data

**Get Console Access:**
```bash
curl -X GET "http://localhost:8080/api/v1/vms/101/console?type=novnc"
```

Response:
```json
{
  "url": "https://proxmox-01:8006/?console=kvm&novnc=1&vmid=101&vmname=web-server-01&node=proxmox-01&resize=off",
  "ticket": "PVE:root@pam:67890ABC::iQD+b2kXVMi6hYfDsgtPsw==",
  "port": 5900,
  "protocol": "wss",
  "host": "proxmox-01",
  "password": "SeCrEtPaSsWoRd",
  "cert": "-----BEGIN CERTIFICATE-----\nMIIDnTCCAoWgAwIBAgIUY...\n-----END CERTIFICATE-----"
}
```

### Key Display Elements
- **Console Launcher**:
  - Console type selector (noVNC, SPICE, xterm.js)
  - Full-screen option
  - New window option
  - Clipboard integration
- **Embedded Console**:
  - iFrame with console
  - Toolbar with actions (Ctrl+Alt+Del, clipboard, fullscreen)
  - Connection status indicator
  - Reconnect button
- **Console Settings**:
  - Keyboard layout
  - Display resolution
  - Scaling options
  - Local cursor

## Audit & Activity Log

### Purpose
Track all operations performed through Moxxie for compliance and troubleshooting.

### Data Requirements
- Operation history
- User attribution
- Resource changes
- Success/failure status
- Detailed operation data

### API Calls & Example Data

**Get Audit Log:**
```bash
curl -X GET "http://localhost:8080/api/v1/admin/audit?start=2024-06-20T00:00:00Z&limit=10"
```

Response:
```json
[
  {
    "id": "audit-67890",
    "timestamp": "2024-06-21T15:30:45Z",
    "user": "admin@pam",
    "sourceIp": "192.168.1.100",
    "operation": "VM_CREATE",
    "resourceType": "VM",
    "resourceId": "103",
    "node": "proxmox-01",
    "details": {
      "vmName": "test-server-01",
      "cores": 2,
      "memory": 4096,
      "disk": "50G",
      "tags": ["env-test", "temporary"]
    },
    "success": true,
    "duration": 12.5,
    "task": "UPID:proxmox-01:00001234:00ABCDEF:67890ABC:qmcreate:103:root@pam:"
  },
  {
    "id": "audit-67891",
    "timestamp": "2024-06-21T15:35:00Z",
    "user": "operator@pve",
    "sourceIp": "192.168.1.101",
    "operation": "BULK_SNAPSHOT",
    "resourceType": "BULK",
    "resourceId": "bulk-op-12346",
    "node": null,
    "details": {
      "vmCount": 5,
      "snapshotName": "pre-update-20240621",
      "successful": 5,
      "failed": 0
    },
    "success": true,
    "duration": 45.2
  },
  {
    "id": "audit-67892",
    "timestamp": "2024-06-21T15:40:00Z",
    "user": "admin@pam",
    "sourceIp": "192.168.1.100",
    "operation": "VM_DELETE",
    "resourceType": "VM",
    "resourceId": "99",
    "node": "proxmox-02",
    "details": {
      "vmName": "old-test-vm",
      "reason": "No longer needed",
      "purge": true
    },
    "success": false,
    "error": "VM has protected backups",
    "duration": 0.5
  }
]
```

### Key Display Elements
- **Audit Log Table**:
  - Timestamp (sortable)
  - User with source IP
  - Operation type with icon
  - Resource affected
  - Success/failure indicator
  - Duration
  - Expandable details
- **Filters**:
  - Date range picker
  - User selector
  - Operation type filter
  - Success/failure filter
  - Resource type filter
  - Text search
- **Details Panel** (on row expansion):
  - Full operation parameters
  - Error details if failed
  - Related task IDs
  - Before/after state for updates
- **Export Options**:
  - CSV export
  - Date range selection
  - Filter preservation

## Common UI Components

### Tag Chips
- Color-coded based on category
- Show tag name
- Optional count badge
- Clickable to filter by tag
- X button to remove (in edit mode)

### Resource Usage Bars
- Show current usage vs total
- Color coding: Green (<70%), Yellow (70-90%), Red (>90%)
- Tooltip with exact values
- Optional threshold indicators

### Status Indicators
- VM Status: Running (green), Stopped (gray), Paused (yellow), Error (red)
- Task Status: Running (blue pulse), Success (green check), Failed (red X)
- Node Status: Online (green), Offline (red), Unknown (gray)

### Action Menus
- Primary action as button
- Secondary actions in dropdown
- Dangerous actions in red with confirmation
- Disabled actions with tooltip explaining why

### Data Tables
- Sortable columns
- Resizable columns
- Row selection with checkbox
- Sticky header on scroll
- Pagination or infinite scroll
- Column visibility toggle
- Export functionality

## API Response Patterns

### Success Response
```json
{
  "data": { /* resource data */ },
  "message": "Operation completed successfully"
}
```

### Error Response
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid VM configuration",
  "status": 400,
  "timestamp": "2024-06-21T10:30:00Z",
  "details": {
    "memory": "Must be multiple of 1024"
  }
}
```

### Async Operation Response
```json
{
  "task": "UPID:proxmox-01:00001234:00ABCDEF:12345678:qmcreate:101:root@pam:",
  "message": "Operation started",
  "estimatedDuration": 30
}
```

### List Response with Metadata
```json
{
  "data": [ /* array of items */ ],
  "pagination": {
    "total": 150,
    "page": 1,
    "pageSize": 20,
    "hasNext": true,
    "hasPrevious": false
  },
  "filters": {
    "applied": ["status:running", "tag:env-prod"]
  }
}
```

## Notes for Implementation

1. **Real-time Updates**: Consider WebSocket connections for:
   - Task progress monitoring
   - VM status changes
   - Resource usage updates

2. **Error Handling**: Always show meaningful error messages from the API response

3. **Loading States**: Show skeletons or spinners while data loads

4. **Empty States**: Helpful messages when no data is available

5. **Confirmations**: Always confirm destructive actions (delete, stop, etc.)

6. **Accessibility**: Ensure keyboard navigation and screen reader support

7. **Responsive Design**: Tables should work on mobile (consider card view)

8. **Performance**: Implement virtual scrolling for large lists

9. **Caching**: Cache relatively static data (nodes, storage types)

10. **Batch Operations**: Show clear progress for multi-item operations

## Cluster Provisioning

### Purpose
Provision complete Kubernetes clusters (Talos, K3s, or generic) with automated node creation and configuration.

### Data Requirements
- Cluster types available
- Node templates and sizing
- Network configurations
- Storage options per node
- Provisioning progress tracking
- Operation history

### API Calls & Example Data

**Provision a Talos Cluster:**
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d '{
    "name": "talos-prod-01",
    "type": "TALOS",
    "nodeGroups": [
      {
        "name": "control-plane",
        "role": "CONTROL_PLANE",
        "count": 3,
        "template": {
          "cores": 4,
          "memory": 8192,
          "diskSize": "100G",
          "storagePool": "local-zfs"
        },
        "placementConstraints": {
          "spreadAcrossNodes": true,
          "allowedNodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
          "nodeSelector": {}
        },
        "cloudInit": {
          "userData": "# Talos control plane config\n...",
          "networkConfig": "version: 2\nethernets:\n  eth0:\n    dhcp4: false\n    addresses: [192.168.100.10/24]"
        }
      },
      {
        "name": "workers",
        "role": "WORKER",
        "count": 5,
        "template": {
          "cores": 8,
          "memory": 16384,
          "diskSize": "200G",
          "storagePool": "local-zfs"
        },
        "placementConstraints": {
          "spreadAcrossNodes": true
        }
      }
    ],
    "networkTopology": {
      "podNetwork": "10.244.0.0/16",
      "serviceNetwork": "10.96.0.0/12",
      "vip": "192.168.100.10",
      "controlPlaneEndpoint": "talos-prod-01.local:6443",
      "additionalNetworks": [
        {
          "name": "storage",
          "vlan": 200,
          "subnet": "192.168.200.0/24"
        }
      ]
    },
    "globalCloudInit": {
      "sshKeys": ["ssh-ed25519 AAAAC3NzaC1lZDI1NTE5... admin@local"],
      "packages": [],
      "users": []
    },
    "metadata": {
      "environment": "production",
      "owner": "platform-team",
      "costCenter": "infrastructure"
    },
    "options": {
      "startAfterCreation": true,
      "waitForReady": true,
      "timeoutMinutes": 30,
      "dryRun": false
    }
  }'
```

Response:
```json
{
  "operationId": "cluster-prov-550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "message": "Cluster provisioning started",
  "clusterName": "talos-prod-01",
  "clusterType": "TALOS",
  "startTime": "2024-06-21T10:00:00Z",
  "estimatedCompletion": "2024-06-21T10:30:00Z",
  "nodeCount": 8,
  "progress": {
    "percentage": 0,
    "currentPhase": "INITIALIZING",
    "currentOperation": "Validating cluster specification"
  },
  "links": {
    "self": "http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000",
    "cancel": "http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000/cancel"
  }
}
```

**Get Provisioning Status:**
```bash
curl -X GET http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000
```

Response (In Progress):
```json
{
  "operationId": "cluster-prov-550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "message": "Creating worker nodes",
  "clusterName": "talos-prod-01",
  "clusterType": "TALOS",
  "startTime": "2024-06-21T10:00:00Z",
  "estimatedCompletion": "2024-06-21T10:30:00Z",
  "nodeCount": 8,
  "progress": {
    "percentage": 65,
    "currentPhase": "CREATING_NODES",
    "currentOperation": "Creating worker node 3 of 5",
    "phasesCompleted": ["INITIALIZING", "CREATING_CONTROL_PLANE"],
    "phasesRemaining": ["CREATING_WORKERS", "CONFIGURING_NETWORK", "BOOTSTRAPPING", "VALIDATING"],
    "nodesCreated": 5,
    "nodesRemaining": 3
  },
  "nodes": [
    {
      "vmId": 201,
      "name": "talos-prod-01-cp-1",
      "role": "CONTROL_PLANE",
      "node": "proxmox-01",
      "status": "running",
      "ipAddress": "192.168.100.11"
    },
    {
      "vmId": 202,
      "name": "talos-prod-01-cp-2",
      "role": "CONTROL_PLANE",
      "node": "proxmox-02",
      "status": "running",
      "ipAddress": "192.168.100.12"
    },
    {
      "vmId": 203,
      "name": "talos-prod-01-cp-3",
      "role": "CONTROL_PLANE",
      "node": "proxmox-03",
      "status": "running",
      "ipAddress": "192.168.100.13"
    },
    {
      "vmId": 204,
      "name": "talos-prod-01-worker-1",
      "role": "WORKER",
      "node": "proxmox-01",
      "status": "running",
      "ipAddress": "192.168.100.21"
    },
    {
      "vmId": 205,
      "name": "talos-prod-01-worker-2",
      "role": "WORKER",
      "node": "proxmox-02",
      "status": "running",
      "ipAddress": "192.168.100.22"
    }
  ],
  "links": {
    "self": "http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000",
    "cancel": "http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000/cancel"
  }
}
```

Response (Completed):
```json
{
  "operationId": "cluster-prov-550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "message": "Cluster provisioned successfully",
  "clusterName": "talos-prod-01",
  "clusterType": "TALOS",
  "startTime": "2024-06-21T10:00:00Z",
  "completionTime": "2024-06-21T10:28:45Z",
  "nodeCount": 8,
  "progress": {
    "percentage": 100,
    "currentPhase": "COMPLETED",
    "phasesCompleted": ["INITIALIZING", "CREATING_CONTROL_PLANE", "CREATING_WORKERS", "CONFIGURING_NETWORK", "BOOTSTRAPPING", "VALIDATING"]
  },
  "nodes": [
    /* ... all 8 nodes with their details ... */
  ],
  "clusterAccess": {
    "controlPlaneEndpoint": "https://192.168.100.10:6443",
    "talosEndpoint": "192.168.100.10:50000",
    "kubeconfig": "# Retrieve using talosctl or from first control plane node"
  },
  "tags": ["cluster-talos-prod-01", "talos-cluster", "env-prod"],
  "links": {
    "self": "http://localhost:8080/api/v1/clusters/operations/cluster-prov-550e8400-e29b-41d4-a716-446655440000",
    "nodes": "http://localhost:8080/api/v1/vms?tags=cluster-talos-prod-01"
  }
}
```

**List All Provisioning Operations:**
```bash
curl -X GET http://localhost:8080/api/v1/clusters/operations
```

Response:
```json
[
  {
    "operationId": "cluster-prov-550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "clusterName": "talos-prod-01",
    "clusterType": "TALOS",
    "startTime": "2024-06-21T10:00:00Z",
    "completionTime": "2024-06-21T10:28:45Z",
    "nodeCount": 8
  },
  {
    "operationId": "cluster-prov-660e8400-e29b-41d4-a716-446655440001",
    "status": "IN_PROGRESS",
    "clusterName": "k3s-dev-01",
    "clusterType": "K3S",
    "startTime": "2024-06-21T11:00:00Z",
    "progress": {
      "percentage": 25,
      "currentPhase": "CREATING_CONTROL_PLANE"
    }
  },
  {
    "operationId": "cluster-prov-770e8400-e29b-41d4-a716-446655440002",
    "status": "FAILED",
    "clusterName": "talos-test-01",
    "clusterType": "TALOS",
    "startTime": "2024-06-21T09:00:00Z",
    "completionTime": "2024-06-21T09:05:00Z",
    "error": "Insufficient resources on node proxmox-03"
  }
]
```

### Key Display Elements

- **Cluster Creation Wizard**:
  1. **Cluster Type Selection**:
     - Talos Linux (with version selection)
     - K3s (lightweight)
     - Generic VM cluster
     - Type-specific options and presets
  
  2. **Node Groups Configuration**:
     - Control plane nodes (count, sizing)
     - Worker nodes (count, sizing) 
     - Node template builder:
       - CPU cores slider
       - Memory selector
       - Disk size and storage pool
       - Network interfaces
     - Placement constraints:
       - Spread across nodes toggle
       - Node affinity rules
       - Anti-affinity settings
  
  3. **Network Configuration**:
     - Pod network CIDR
     - Service network CIDR
     - Control plane VIP
     - Additional networks (storage, management)
     - VLAN assignments
  
  4. **Cloud-Init / Configuration**:
     - SSH keys input
     - Initial user setup
     - Custom cloud-init for different node groups
     - Talos machine config upload (optional)
  
  5. **Review & Deploy**:
     - Cost estimation (resources)
     - Validation warnings
     - Dry run option
     - Deploy button

- **Provisioning Progress View**:
  - Overall progress bar with percentage
  - Phase tracker showing current operation
  - Real-time node creation status
  - Log viewer for detailed progress
  - Estimated time remaining
  - Cancel operation button (if still possible)
  - Node creation table with status per VM

- **Operation History**:
  - List of all cluster provisioning operations
  - Status indicators (completed, failed, in-progress)
  - Duration and resource count
  - Quick actions (view details, retry failed)
  - Filter by status, cluster type, date range

## Talos Cluster Management

### Purpose
Specialized management interface for Talos Linux Kubernetes clusters with Talos-specific operations.

### Data Requirements
- Talos cluster identification
- Node roles and Talos version
- Cluster health status
- Talos configuration
- Upgrade availability
- Etcd status

### API Calls & Example Data

**Get Talos Cluster Details:**
```bash
curl -X GET "http://localhost:8080/api/v1/vms?tags=talos-cluster,cluster-talos-prod-01"
```

Response:
```json
[
  {
    "vmid": 201,
    "name": "talos-prod-01-cp-1",
    "node": "proxmox-01",
    "status": "running",
    "tags": ["talos-cluster", "cluster-talos-prod-01", "talos-control-plane", "talos-v1.7.0"],
    "cpu": 0.12,
    "mem": 6442450944,
    "uptime": 86400
  },
  {
    "vmid": 202,
    "name": "talos-prod-01-cp-2",
    "node": "proxmox-02",
    "status": "running",
    "tags": ["talos-cluster", "cluster-talos-prod-01", "talos-control-plane", "talos-v1.7.0"],
    "cpu": 0.10,
    "mem": 6174015488,
    "uptime": 86400
  }
]
```

**Perform Talos Upgrade (via Moxxie orchestration):**
```bash
curl -X POST http://localhost:8080/api/v1/clusters/talos-prod-01/upgrade \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "v1.7.1",
    "upgradeStrategy": "ROLLING",
    "validateOnly": false,
    "preserveConfig": true
  }'
```

Response:
```json
{
  "upgradeId": "upgrade-880e8400-e29b-41d4-a716-446655440000",
  "clusterName": "talos-prod-01",
  "currentVersion": "v1.7.0",
  "targetVersion": "v1.7.1",
  "status": "IN_PROGRESS",
  "strategy": "ROLLING",
  "nodesTotal": 8,
  "nodesUpgraded": 0,
  "currentNode": "talos-prod-01-cp-1",
  "estimatedCompletion": "2024-06-21T12:00:00Z"
}
```

**Scale Talos Cluster:**
```bash
curl -X POST http://localhost:8080/api/v1/clusters/talos-prod-01/scale \
  -H "Content-Type: application/json" \
  -d '{
    "nodeGroup": "workers",
    "targetCount": 8,
    "scalingStrategy": "GRADUAL"
  }'
```

Response:
```json
{
  "operationId": "scale-990e8400-e29b-41d4-a716-446655440000",
  "clusterName": "talos-prod-01",
  "nodeGroup": "workers",
  "currentCount": 5,
  "targetCount": 8,
  "nodesToAdd": 3,
  "status": "IN_PROGRESS",
  "newNodes": [
    {
      "vmId": 209,
      "name": "talos-prod-01-worker-6",
      "status": "creating"
    }
  ]
}
```

### Key Display Elements

- **Talos Cluster Overview**:
  - Cluster name and status badge
  - Talos version with upgrade available indicator
  - Node count by role (control plane, workers)
  - Kubernetes version
  - API endpoint and VIP status
  - Etcd health status
  - Certificate expiration warnings

- **Node Management Grid**:
  - **Control Plane Section**:
    - Node health indicators
    - Etcd member status
    - Leader election status
    - Certificate status
    - Per-node actions (cordon, drain, reboot, upgrade)
  
  - **Worker Nodes Section**:
    - Node health and readiness
    - Workload count
    - Resource usage bars
    - Quick actions (cordon, drain, delete)

- **Cluster Operations Panel**:
  - **Upgrade Management**:
    - Current version display
    - Available upgrades list
    - Upgrade strategy selector (rolling, parallel)
    - Pre-flight check results
    - Upgrade progress tracker
  
  - **Scaling Operations**:
    - Node group selector
    - Target count input with +/- buttons
    - Resource availability check
    - Preview of new nodes
    - Scaling progress
  
  - **Configuration Management**:
    - Download current Talos config
    - Apply configuration patches
    - Backup etcd
    - Reset nodes

- **Maintenance Mode**:
  - Cluster-wide maintenance toggle
  - Node cordoning interface
  - Workload migration status
  - Maintenance window scheduling

- **Talos-Specific Monitoring**:
  - Control plane API health
  - Etcd metrics and status
  - Certificate expiration timeline
  - Kubernetes component health
  - Network connectivity matrix

### Integration with General VM Management

Talos cluster nodes appear in regular VM lists but with special indicators:
- Talos icon/badge on VM cards
- Cluster membership shown in tags
- Special warning on individual VM operations
- Link to cluster management from VM detail

### Cluster Destruction

**Safe Cluster Deletion:**
```bash
curl -X DELETE http://localhost:8080/api/v1/clusters/talos-prod-01 \
  -H "Content-Type: application/json" \
  -d '{
    "confirmation": "talos-prod-01",
    "deleteData": true,
    "force": false
  }'
```

Response:
```json
{
  "operationId": "destroy-aa0e8400-e29b-41d4-a716-446655440000",
  "clusterName": "talos-prod-01",
  "status": "IN_PROGRESS",
  "nodesToDelete": 8,
  "phases": [
    "DRAINING_NODES",
    "BACKING_UP_ETCD",
    "DELETING_WORKERS",
    "DELETING_CONTROL_PLANE",
    "CLEANUP"
  ],
  "currentPhase": "DRAINING_NODES",
  "estimatedCompletion": "2024-06-21T13:00:00Z"
}
```

### Key Safety Features

- **Cluster Protection**:
  - Confirmation string required for deletion
  - Production clusters require additional approval
  - Automatic etcd backup before destruction
  - Workload migration warnings

- **Operation Validation**:
  - Pre-flight checks for all operations
  - Resource availability verification
  - Network connectivity tests
  - Certificate validation

- **Rollback Capabilities**:
  - Upgrade rollback support
  - Configuration restore points
  - Snapshot before major operations