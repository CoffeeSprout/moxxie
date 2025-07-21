# Moxxie UI Pages and Data Requirements

This document lists all UI pages needed for Moxxie with their data requirements and sample data for mockup creation.

## 1. Dashboard Page

**Purpose**: Overview of cluster health and activity

**Data Elements**:
- Cluster name and status
- Node count and health
- Total VMs (running/stopped/total)
- Resource usage (CPU/Memory/Storage)
- Recent tasks (last 5-10)
- Active alerts

**Sample Data**:
```json
{
  "cluster": {
    "name": "production-cluster",
    "healthy": true,
    "nodeCount": 3,
    "nodesOnline": 3
  },
  "vmStats": {
    "total": 45,
    "running": 38,
    "stopped": 7,
    "templates": 3
  },
  "resources": {
    "cpu": {
      "used": 124.5,
      "total": 384,
      "percentage": 32.4
    },
    "memory": {
      "used": 412,
      "total": 768,
      "percentage": 53.6,
      "unit": "GB"
    },
    "storage": {
      "used": 8.2,
      "total": 20,
      "percentage": 41.0,
      "unit": "TB"
    }
  },
  "recentTasks": [
    {
      "id": "UPID:proxmox-01:00001234",
      "type": "qmstart",
      "status": "OK",
      "user": "admin@pve",
      "startTime": "2024-12-21T10:30:00Z",
      "node": "proxmox-01",
      "description": "Start VM 101"
    },
    {
      "id": "UPID:proxmox-02:00001235",
      "type": "qmcreate",
      "status": "running",
      "user": "admin@pve",
      "startTime": "2024-12-21T10:32:00Z",
      "node": "proxmox-02",
      "description": "Create VM 8205",
      "progress": 65
    }
  ],
  "alerts": [
    {
      "severity": "warning",
      "message": "Node proxmox-03 storage usage above 80%",
      "timestamp": "2024-12-21T10:15:00Z"
    }
  ]
}
```

## 2. VM List Page

**Purpose**: View and manage all VMs with filtering

**Data Elements**:
- VM list with status, resources, tags
- Filter options (status, tags, client, node)
- Bulk action buttons
- Search box

**Sample Data**:
```json
{
  "vms": [
    {
      "vmid": 101,
      "name": "web-server-01",
      "node": "proxmox-01",
      "status": "running",
      "uptime": 864000,
      "cpu": 0.05,
      "cpus": 4,
      "memory": 4294967296,
      "maxMemory": 8589934592,
      "disk": 10737418240,
      "maxDisk": 107374182400,
      "tags": ["env-prod", "client-acme", "always-on"],
      "ip": "10.0.1.101",
      "os": "Ubuntu 22.04"
    },
    {
      "vmid": 102,
      "name": "db-master-01",
      "node": "proxmox-02",
      "status": "running",
      "uptime": 432000,
      "cpu": 0.23,
      "cpus": 8,
      "memory": 17179869184,
      "maxMemory": 34359738368,
      "disk": 53687091200,
      "maxDisk": 536870912000,
      "tags": ["env-prod", "client-acme", "always-on", "db-cluster"],
      "ip": "10.0.1.102",
      "os": "PostgreSQL 15"
    }
  ],
  "filters": {
    "availableTags": ["env-prod", "env-dev", "client-acme", "client-nexus", "always-on"],
    "availableNodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
    "availableStatuses": ["running", "stopped", "suspended"]
  }
}
```

## 3. VM Detail Page

**Purpose**: Detailed view and management of single VM

**Data Elements**:
- VM status and basic info
- Resource usage graphs
- Network interfaces
- Disk configuration
- Snapshots list
- Recent backups
- Console access button
- Power controls
- Migration option

**Sample Data**:
```json
{
  "vm": {
    "vmid": 101,
    "name": "web-server-01",
    "node": "proxmox-01",
    "status": "running",
    "uptime": 864000,
    "created": "2024-01-15T08:00:00Z",
    "tags": ["env-prod", "client-acme", "always-on"],
    "os": "Ubuntu 22.04 LTS",
    "agent": true,
    "lock": null
  },
  "resources": {
    "cpu": {
      "current": 0.05,
      "cores": 4,
      "sockets": 1,
      "type": "host"
    },
    "memory": {
      "used": 3.8,
      "total": 8,
      "unit": "GB",
      "balloon": true
    }
  },
  "networks": [
    {
      "interface": "net0",
      "mac": "BC:24:11:4E:7A:9B",
      "bridge": "vmbr0",
      "ip": "10.0.1.101/24",
      "gateway": "10.0.1.1",
      "firewall": true,
      "rate": null
    }
  ],
  "disks": [
    {
      "device": "scsi0",
      "size": "100G",
      "storage": "local-lvm",
      "format": "raw",
      "cache": "none",
      "ssd": true,
      "iothread": true
    }
  ],
  "snapshots": [
    {
      "name": "before-upgrade",
      "time": "2024-12-15T10:00:00Z",
      "description": "Snapshot before system upgrade",
      "size": "45GB",
      "hasVmState": false
    }
  ],
  "backups": [
    {
      "id": "vzdump-qemu-101-2024_12_20-02_00_00.vma.zst",
      "time": "2024-12-20T02:00:00Z",
      "size": "32GB",
      "storage": "backup-nfs",
      "mode": "snapshot",
      "notes": "Daily backup"
    }
  ]
}
```

## 4. Create VM Page

**Purpose**: Create new VMs with various configurations

**Data Elements**:
- VM name and ID
- Node selection
- Resource allocation (CPU, Memory)
- Disk configuration
- Network configuration
- OS template selection
- Cloud-init settings
- Tags

**Sample Data**:
```json
{
  "availableNodes": [
    {"name": "proxmox-01", "online": true, "cpu": 32.4, "memory": 45.2},
    {"name": "proxmox-02", "online": true, "cpu": 28.1, "memory": 38.7},
    {"name": "proxmox-03", "online": true, "cpu": 41.3, "memory": 52.1}
  ],
  "templates": [
    {"id": 9000, "name": "ubuntu-22.04-template", "size": "2.5GB"},
    {"id": 9001, "name": "debian-12-template", "size": "2.1GB"},
    {"id": 9002, "name": "rockylinux-9-template", "size": "2.8GB"}
  ],
  "storage": [
    {"name": "local-lvm", "type": "lvm", "available": "450GB"},
    {"name": "ceph-pool", "type": "rbd", "available": "8.5TB"},
    {"name": "nfs-storage", "type": "nfs", "available": "12TB"}
  ],
  "networks": [
    {"name": "vmbr0", "type": "bridge", "cidr": "10.0.1.0/24"},
    {"name": "vmbr1", "type": "bridge", "cidr": "10.0.2.0/24"}
  ],
  "suggestedTags": ["env-dev", "env-prod", "client-new", "moxxie"]
}
```

## 5. Snapshots Page

**Purpose**: Manage VM snapshots

**Data Elements**:
- Snapshot list with hierarchy
- Create snapshot form
- Rollback confirmation
- Delete options
- Snapshot details

**Sample Data**:
```json
{
  "vmInfo": {
    "vmid": 101,
    "name": "web-server-01"
  },
  "snapshots": [
    {
      "name": "clean-install",
      "time": "2024-01-15T08:30:00Z",
      "parent": null,
      "description": "Fresh OS installation",
      "size": "8.2GB",
      "hasVmState": false,
      "children": ["after-config"]
    },
    {
      "name": "after-config",
      "time": "2024-01-15T12:00:00Z",
      "parent": "clean-install",
      "description": "Base configuration complete",
      "size": "12.5GB",
      "hasVmState": false,
      "children": ["before-upgrade"]
    },
    {
      "name": "before-upgrade",
      "time": "2024-12-15T10:00:00Z",
      "parent": "after-config",
      "description": "Snapshot before system upgrade",
      "size": "45GB",
      "hasVmState": false,
      "children": [],
      "current": true
    }
  ]
}
```

## 6. Backup Management Page

**Purpose**: View and manage VM backups

**Data Elements**:
- Backup list by storage
- Backup details (size, compression, notes)
- Restore options
- Delete/protect controls
- Storage usage

**Sample Data**:
```json
{
  "storage": [
    {
      "name": "backup-nfs",
      "type": "nfs",
      "used": 2.4,
      "total": 10,
      "unit": "TB"
    }
  ],
  "backups": [
    {
      "id": "vzdump-qemu-101-2024_12_20-02_00_00.vma.zst",
      "vmid": 101,
      "vmName": "web-server-01",
      "time": "2024-12-20T02:00:00Z",
      "size": "32GB",
      "storage": "backup-nfs",
      "mode": "snapshot",
      "compression": "zstd",
      "protected": false,
      "notes": "Daily backup",
      "verified": true
    },
    {
      "id": "vzdump-qemu-102-2024_12_20-02_15_00.vma.zst",
      "vmid": 102,
      "vmName": "db-master-01",
      "time": "2024-12-20T02:15:00Z",
      "size": "128GB",
      "storage": "backup-nfs",
      "mode": "snapshot",
      "compression": "zstd",
      "protected": true,
      "notes": "Daily backup - Production DB",
      "verified": true
    }
  ]
}
```

## 7. Scheduler Page

**Purpose**: Manage automated tasks

**Data Elements**:
- Scheduled job list
- Job execution history
- Create/edit job forms
- Enable/disable controls
- Manual trigger button

**Sample Data**:
```json
{
  "jobs": [
    {
      "id": "job-001",
      "name": "daily-snapshots",
      "type": "snapshot_create",
      "schedule": "0 2 * * *",
      "enabled": true,
      "lastRun": "2024-12-21T02:00:00Z",
      "lastStatus": "success",
      "nextRun": "2024-12-22T02:00:00Z",
      "targets": "TAG: env-prod AND NOT always-on",
      "parameters": {
        "namePattern": "auto-{date}",
        "keepLast": 7
      }
    },
    {
      "id": "job-002",
      "name": "weekend-backups",
      "type": "backup_create",
      "schedule": "0 3 * * 6",
      "enabled": true,
      "lastRun": "2024-12-16T03:00:00Z",
      "lastStatus": "success",
      "nextRun": "2024-12-23T03:00:00Z",
      "targets": "ALL VMs",
      "parameters": {
        "storage": "backup-nfs",
        "mode": "snapshot",
        "compression": "zstd"
      }
    }
  ],
  "taskTypes": [
    "snapshot_create",
    "snapshot_delete",
    "backup_create",
    "vm_start",
    "vm_stop"
  ]
}
```

## 8. Storage Overview Page

**Purpose**: Monitor storage pools and usage

**Data Elements**:
- Storage pool list
- Usage statistics
- Content types
- Performance metrics
- Health status

**Sample Data**:
```json
{
  "storage": [
    {
      "id": "local-lvm",
      "type": "lvmthin",
      "content": ["images", "rootdir"],
      "nodes": ["proxmox-01"],
      "enabled": true,
      "shared": false,
      "used": 450,
      "total": 1000,
      "available": 550,
      "unit": "GB",
      "health": "healthy"
    },
    {
      "id": "ceph-pool",
      "type": "rbd",
      "content": ["images", "rootdir"],
      "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
      "enabled": true,
      "shared": true,
      "used": 8.5,
      "total": 20,
      "available": 11.5,
      "unit": "TB",
      "health": "healthy",
      "performance": {
        "iops": 45000,
        "throughput": "1.2GB/s"
      }
    },
    {
      "id": "backup-nfs",
      "type": "nfs",
      "content": ["backup", "iso", "vztmpl"],
      "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"],
      "enabled": true,
      "shared": true,
      "used": 2.4,
      "total": 10,
      "available": 7.6,
      "unit": "TB",
      "health": "healthy",
      "server": "10.0.0.50",
      "export": "/mnt/backups"
    }
  ]
}
```

## 9. Network Overview Page

**Purpose**: View network configuration and SDN

**Data Elements**:
- Network bridges
- VLANs
- SDN zones and VNets
- IP pools
- Network topology

**Sample Data**:
```json
{
  "bridges": [
    {
      "name": "vmbr0",
      "type": "bridge",
      "active": true,
      "ports": ["enp1s0"],
      "address": "10.0.0.10/24",
      "gateway": "10.0.0.1",
      "vlanAware": true,
      "mtu": 1500
    },
    {
      "name": "vmbr1",
      "type": "bridge",
      "active": true,
      "ports": ["enp2s0"],
      "address": "10.1.0.10/24",
      "vlanAware": true,
      "mtu": 9000
    }
  ],
  "sdnZones": [
    {
      "name": "localnet",
      "type": "simple",
      "nodes": ["proxmox-01", "proxmox-02", "proxmox-03"]
    }
  ],
  "vnets": [
    {
      "name": "dmz",
      "zone": "localnet",
      "tag": 100,
      "alias": "DMZ Network",
      "subnets": ["192.168.100.0/24"]
    },
    {
      "name": "internal",
      "zone": "localnet",
      "tag": 200,
      "alias": "Internal Network",
      "subnets": ["192.168.200.0/24"]
    }
  ]
}
```

## 10. Node Management Page

**Purpose**: Monitor and manage cluster nodes

**Data Elements**:
- Node list with status
- Resource usage per node
- Services status
- Update status
- Certificates

**Sample Data**:
```json
{
  "nodes": [
    {
      "name": "proxmox-01",
      "online": true,
      "ip": "10.0.0.10",
      "uptime": 864000,
      "version": "8.1.3",
      "kernelVersion": "6.5.11-7-pve",
      "cpuModel": "Intel Xeon Gold 6248R",
      "resources": {
        "cpu": {
          "cores": 48,
          "threads": 96,
          "usage": 32.4,
          "loadAvg": [2.3, 2.1, 1.9]
        },
        "memory": {
          "total": 256,
          "used": 115.8,
          "unit": "GB"
        },
        "storage": {
          "root": {
            "total": 100,
            "used": 23.4,
            "unit": "GB"
          }
        }
      },
      "services": [
        {"name": "pve-cluster", "state": "active"},
        {"name": "pvedaemon", "state": "active"},
        {"name": "pveproxy", "state": "active"}
      ]
    }
  ]
}
```

## 11. Migration Page

**Purpose**: Migrate VMs between nodes

**Data Elements**:
- Source VM details
- Target node selection
- Migration options
- Compatibility checks
- Progress tracking

**Sample Data**:
```json
{
  "vm": {
    "vmid": 101,
    "name": "web-server-01",
    "currentNode": "proxmox-01",
    "status": "running",
    "memory": 8192,
    "disks": [
      {
        "device": "scsi0",
        "size": "100G",
        "storage": "local-lvm",
        "shared": false
      }
    ]
  },
  "targetNodes": [
    {
      "name": "proxmox-02",
      "compatible": true,
      "cpu": 28.1,
      "memory": 38.7,
      "warnings": []
    },
    {
      "name": "proxmox-03",
      "compatible": true,
      "cpu": 41.3,
      "memory": 52.1,
      "warnings": ["Different CPU generation"]
    }
  ],
  "migrationOptions": {
    "online": true,
    "withLocalDisks": true,
    "targetStorage": {
      "scsi0": ["local-lvm", "ceph-pool"]
    }
  }
}
```

## 12. Tag Management Page

**Purpose**: Manage VM tags and bulk operations

**Data Elements**:
- Tag list with counts
- Tag categories
- VMs per tag
- Bulk tag operations
- Tag colors/styles

**Sample Data**:
```json
{
  "tags": [
    {
      "name": "env-prod",
      "count": 23,
      "category": "environment",
      "color": "red",
      "description": "Production environment"
    },
    {
      "name": "client-acme",
      "count": 15,
      "category": "client",
      "color": "blue",
      "description": "ACME Corporation"
    },
    {
      "name": "always-on",
      "count": 8,
      "category": "criticality",
      "color": "red",
      "description": "Critical VMs that must not be shut down"
    },
    {
      "name": "k8s-worker",
      "count": 12,
      "category": "kubernetes",
      "color": "purple",
      "description": "Kubernetes worker nodes"
    }
  ],
  "categories": [
    {"name": "environment", "prefix": "env-"},
    {"name": "client", "prefix": "client-"},
    {"name": "criticality", "prefix": null},
    {"name": "kubernetes", "prefix": "k8s-"}
  ]
}
```

## 13. Console Access Page

**Purpose**: Remote console access to VMs

**Data Elements**:
- Console type selection (noVNC, xterm.js, SPICE)
- Connection parameters
- Clipboard support
- Keyboard shortcuts
- Full screen option

**Sample Data**:
```json
{
  "vm": {
    "vmid": 101,
    "name": "web-server-01",
    "status": "running"
  },
  "consoleOptions": [
    {
      "type": "novnc",
      "available": true,
      "preferred": true,
      "features": ["clipboard", "resize", "fullscreen"]
    },
    {
      "type": "xterm",
      "available": true,
      "preferred": false,
      "features": ["copy-paste", "resize"]
    },
    {
      "type": "spice",
      "available": false,
      "reason": "SPICE not installed on VM"
    }
  ],
  "connection": {
    "ticket": "PVE:user@pve:1234567890ABCDEF",
    "port": 5901,
    "websocketPort": 5901
  }
}
```

## 14. Activity Log Page

**Purpose**: Audit trail of all operations

**Data Elements**:
- Operation history
- User actions
- Timestamps
- Success/failure status
- Affected resources
- Filtering options

**Sample Data**:
```json
{
  "activities": [
    {
      "id": "act-001",
      "timestamp": "2024-12-21T10:30:00Z",
      "user": "admin@pve",
      "sourceIp": "192.168.1.100",
      "operation": "VM_START",
      "resource": "VM 101 (web-server-01)",
      "node": "proxmox-01",
      "success": true,
      "duration": 2.3,
      "details": {
        "vmid": 101,
        "previousState": "stopped"
      }
    },
    {
      "id": "act-002",
      "timestamp": "2024-12-21T10:32:00Z",
      "user": "admin@pve",
      "sourceIp": "192.168.1.100",
      "operation": "VM_CREATE",
      "resource": "VM 8205",
      "node": "proxmox-02",
      "success": false,
      "duration": 0.5,
      "error": "Storage 'local-lvm' does not have enough free space",
      "details": {
        "requestedDisk": "500G",
        "availableSpace": "450G"
      }
    }
  ],
  "filters": {
    "users": ["admin@pve", "operator@pve", "moxxie@pve"],
    "operations": ["VM_CREATE", "VM_START", "VM_STOP", "VM_DELETE", "SNAPSHOT_CREATE"],
    "dateRange": {
      "start": "2024-12-01",
      "end": "2024-12-21"
    }
  }
}
```

## 15. Cluster Provisioning Page

**Purpose**: Create new VM clusters (Talos, K3s, generic)

**Data Elements**:
- Cluster type selection
- Node group configuration
- Network topology
- Resource allocation
- Progress tracking

**Sample Data**:
```json
{
  "clusterTypes": [
    {
      "type": "TALOS",
      "name": "Talos Linux",
      "description": "Kubernetes-focused OS",
      "icon": "talos-logo.svg",
      "templates": ["talos-v1.6.0", "talos-v1.5.5"]
    },
    {
      "type": "K3S",
      "name": "K3s",
      "description": "Lightweight Kubernetes",
      "icon": "k3s-logo.svg",
      "templates": ["ubuntu-22.04-k3s", "debian-12-k3s"]
    },
    {
      "type": "GENERIC",
      "name": "Generic Cluster",
      "description": "Custom VM cluster",
      "icon": "cluster-generic.svg",
      "templates": ["ubuntu-22.04", "debian-12", "rockylinux-9"]
    }
  ],
  "provisioningOperation": {
    "operationId": "op-123",
    "clusterName": "talos-prod-01",
    "type": "TALOS",
    "status": "PROVISIONING",
    "progress": {
      "current": 3,
      "total": 7,
      "phase": "Creating worker nodes",
      "percentage": 43
    },
    "nodes": [
      {
        "name": "talos-prod-01-cp-1",
        "role": "control-plane",
        "vmid": 8201,
        "status": "completed",
        "ip": "10.0.100.11"
      },
      {
        "name": "talos-prod-01-worker-1",
        "role": "worker",
        "vmid": 8202,
        "status": "creating",
        "ip": "pending"
      }
    ],
    "startTime": "2024-12-21T10:00:00Z",
    "estimatedCompletion": "2024-12-21T10:15:00Z"
  }
}
```

## 16. Settings/Configuration Page

**Purpose**: Configure Moxxie settings and preferences

**Data Elements**:
- Proxmox connection settings
- Default values
- Tag categories
- Notification preferences
- API tokens

**Sample Data**:
```json
{
  "connection": {
    "endpoint": "https://10.0.0.10:8006",
    "verified": true,
    "version": "8.1.3"
  },
  "defaults": {
    "vmCores": 2,
    "vmMemory": 4096,
    "vmDiskSize": "50G",
    "vmBridge": "vmbr0",
    "storagePool": "local-lvm",
    "cpuType": "host"
  },
  "tagCategories": [
    {
      "name": "environment",
      "prefix": "env-",
      "colors": {
        "env-prod": "red",
        "env-staging": "orange",
        "env-dev": "green"
      }
    }
  ],
  "scheduler": {
    "enabled": true,
    "defaultTimezone": "UTC"
  },
  "notifications": {
    "email": {
      "enabled": false,
      "smtp": null
    },
    "webhook": {
      "enabled": false,
      "url": null
    }
  }
}
```