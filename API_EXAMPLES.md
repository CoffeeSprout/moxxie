# Moxxie API Examples

This document provides working examples of Moxxie's REST API endpoints. All examples assume Moxxie is running on `http://localhost:8080`.

## Table of Contents
- [VM Management](#vm-management)
- [Snapshot Management](#snapshot-management)
- [Bulk Snapshot Operations](#bulk-snapshot-operations)
- [Bulk Power Operations](#bulk-power-operations)
- [Tag Management](#tag-management)
- [Scheduler Management](#scheduler-management)
- [Backup Operations](#backup-operations)
- [Bulk Backup Operations](#bulk-backup-operations)
- [VM Migration](#vm-migration)

## VM Management

### List All VMs
```bash
curl -X GET http://localhost:8080/api/v1/vms | jq .
```

### Filter VMs by Tags
```bash
# Filter by multiple tags (AND logic)
curl "http://localhost:8080/api/v1/vms?tags=client:nixz,env:prod" | jq .

# Filter by client (convenience)
curl "http://localhost:8080/api/v1/vms?client=nixz" | jq .
```

### Get Specific VM Details
```bash
curl -X GET http://localhost:8080/api/v1/vms/8200 | jq .
```

### List VMs in Moxxie Pool (8200-8209)
```bash
curl -X GET http://localhost:8080/api/v1/vms | jq '.[] | select(.vmid >= 8200 and .vmid <= 8209) | {vmid, name, status}'
```

### Create VM with Advanced Disk Configuration
```bash
# Create VM with SSD and iothread enabled
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "k8s-worker-01",
    "node": "hv6",
    "cores": 8,
    "memoryMB": 16384,
    "network": {
      "bridge": "vmbr0",
      "vlan": 100
    },
    "bootOrder": "order=net0;scsi0",
    "disks": [
      {
        "interfaceType": "SCSI",
        "slot": 0,
        "storage": "local-zfs",
        "sizeGB": 200,
        "ssd": true,
        "iothread": true,
        "cache": "WRITEBACK",
        "discard": true
      }
    ],
    "tags": ["k8s-worker", "env-prod", "moxxie"]
  }'

# Create VM with multiple disks
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "database-server",
    "node": "hv6",
    "cores": 16,
    "memoryMB": 65536,
    "network": {
      "bridge": "vmbr0"
    },
    "disks": [
      {
        "interfaceType": "SCSI",
        "slot": 0,
        "storage": "local-zfs",
        "sizeGB": 100,
        "ssd": true,
        "iothread": true,
        "cache": "NONE"
      },
      {
        "interfaceType": "SCSI",
        "slot": 1,
        "storage": "local-zfs",
        "sizeGB": 1000,
        "ssd": false,
        "cache": "WRITEBACK",
        "backup": true
      }
    ],
    "tags": ["database", "env-prod"]
  }'

# Create VM with legacy disk configuration (backward compatible)
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "simple-vm",
    "node": "hv6",
    "cores": 2,
    "memoryMB": 4096,
    "diskGB": 50,
    "network": {
      "bridge": "vmbr0"
    }
  }'

# Create VM with custom CPU and VGA types
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "high-perf-vm",
    "node": "hv6",
    "cores": 8,
    "memoryMB": 32768,
    "cpuType": "host",
    "vgaType": "virtio",
    "disks": [
      {
        "interfaceType": "SCSI",
        "slot": 0,
        "storage": "local-zfs",
        "sizeGB": 100,
        "ssd": true,
        "iothread": true
      }
    ],
    "network": {
      "bridge": "vmbr0"
    },
    "tags": ["high-performance"]
  }'

# Create VM from cloud-init image
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 200,
    "name": "k8s-control-01",
    "node": "hv7",
    "cores": 4,
    "memoryMB": 8192,
    "imageSource": "util-iso:images/debian-12-generic-amd64.qcow2",
    "targetStorage": "local-zfs",
    "diskSizeGB": 50,
    "cloudInitUser": "debian",
    "cloudInitPassword": "temppassword123",
    "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGLmQqfp8X5DUVxLruBsCmJ7m4mDGcr5V7e2BXMkNPDp user@example.com",
    "ipConfig": "ip=192.168.1.100/24,gw=192.168.1.1",
    "nameservers": "8.8.8.8,8.8.4.4",
    "searchDomain": "cluster.local",
    "network": {
      "model": "virtio",
      "bridge": "vmbr0"
    },
    "diskOptions": {
      "ssd": true,
      "iothread": true,
      "discard": true
    },
    "cpuType": "host",
    "tags": "k8s-controlplane,env-prod,moxxie",
    "start": true
  }'

# Create VM from cloud image with DHCP
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 201,
    "name": "debian-test",
    "node": "hv7",
    "cores": 2,
    "memoryMB": 4096,
    "imageSource": "util-iso:images/debian-12-cloud.qcow2",
    "targetStorage": "local-zfs",
    "diskSizeGB": 20,
    "cloudInitUser": "admin",
    "cloudInitPassword": "temppass123",
    "ipConfig": "ip=dhcp",
    "network": {
      "model": "virtio",
      "bridge": "vmbr0"
    },
    "qemuAgent": true,
    "start": false
  }'
```

### Workflow: Download Cloud Image and Create VM

```bash
# Step 1: Download cloud image to storage
curl -X POST http://localhost:8080/api/v1/storage/util-iso/download-url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://cloud.debian.org/images/cloud/bookworm/latest/debian-12-generic-amd64.qcow2",
    "filename": "debian-12-generic-amd64.qcow2",
    "checksumUrl": "https://cloud.debian.org/images/cloud/bookworm/latest/SHA512SUMS",
    "checksumAlgorithm": "sha512"
  }'

# Step 2: Create VM from the downloaded image
# NOTE: SSH keys omitted due to Proxmox API bug - use password auth
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 300,
    "name": "k8s-worker-01",
    "node": "hv7",
    "cores": 8,
    "memoryMB": 16384,
    "imageSource": "util-iso:images/debian-12-generic-amd64.qcow2",
    "targetStorage": "local-zfs",
    "diskSizeGB": 100,
    "cloudInitUser": "debian",
    "cloudInitPassword": "changeme123",
    "ipConfig": "ip=dhcp",
    "network": {
      "model": "virtio",
      "bridge": "vmbr0"
    },
    "diskOptions": {
      "ssd": true,
      "iothread": true
    },
    "tags": "k8s-worker,env-prod,moxxie",
    "qemuAgent": true,
    "start": true
  }'

# Create Talos Linux VM from template
# Note: Talos doesn't use traditional users/SSH, only requires network configuration
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 200,
    "name": "talos-vm",
    "node": "storage01",
    "cores": 2,
    "memoryMB": 4096,
    "imageSource": "local-zfs:base-9002-disk-0",
    "targetStorage": "local-zfs",
    "diskSizeGB": 20,
    "ipConfig": "ip=172.17.1.250/16,gw=172.17.1.1",
    "nameservers": "1.1.1.1",
    "network": {
      "model": "virtio",
      "bridge": "workshop"
    },
    "qemuAgent": true,
    "start": true
  }'
```

### Clone VM from Template

Clone an existing VM or template to create a new VM. The new VM ID can be auto-generated if not provided.

```bash
# Clone with auto-generated VM ID
curl -X POST http://localhost:8080/api/v1/vms/9000/clone \
  -H "Content-Type: application/json" \
  -d '{
    "name": "k8s-master-01",
    "targetNode": "hv7",
    "description": "Kubernetes control plane node",
    "fullClone": true,
    "targetStorage": "local-zfs",
    "start": false,
    "tags": ["k8s-controlplane", "env:prod"]
  }'

# Clone with specific VM ID
curl -X POST http://localhost:8080/api/v1/vms/9000/clone \
  -H "Content-Type: application/json" \
  -d '{
    "newVmId": 201,
    "name": "k8s-worker-01",
    "targetNode": "hv6",
    "description": "Kubernetes worker node",
    "fullClone": true,
    "targetStorage": "local-zfs",
    "pool": "kubernetes",
    "tags": ["k8s-worker", "env:prod"]
  }'

# Linked clone (faster, uses less storage)
curl -X POST http://localhost:8080/api/v1/vms/9000/clone \
  -H "Content-Type: application/json" \
  -d '{
    "newVmId": 202,
    "name": "test-vm",
    "targetNode": "hv7",
    "fullClone": false,
    "targetStorage": "local-zfs"
  }'
```

## Snapshot Management

### List Snapshots for a VM
```bash
curl -X GET http://localhost:8080/api/v1/vms/8200/snapshots | jq .
```

### Create Single Snapshot with TTL
```bash
# Create snapshot with 4-hour TTL
curl -X POST http://localhost:8080/api/v1/vms/8200/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-20240621",
    "description": "Before system updates",
    "ttlHours": 4
  }' | jq .
```

### Delete a Snapshot
```bash
curl -X DELETE "http://localhost:8080/api/v1/vms/8200/snapshots/pre-update-20240621" | jq .
```

## Bulk Snapshot Operations

### Create Snapshots by VM IDs
```bash
# Dry run first
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "snapshotName": "bulk-{vm}-{date}",
    "description": "Bulk snapshot test",
    "ttlHours": 24,
    "dryRun": true
  }' | jq .

# Actual creation
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "snapshotName": "maint-{vm}-{date}",
    "description": "Pre-maintenance snapshot",
    "ttlHours": 24,
    "dryRun": false
  }' | jq .
```

### Create Snapshots by Name Pattern
```bash
# Snapshot all worker nodes
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-wk-*"}
    ],
    "snapshotName": "workers-backup-{date}",
    "description": "Worker nodes backup",
    "ttlHours": 48,
    "maxParallel": 3
  }' | jq .
```

### Create Snapshots by Tag Expression
```bash
# Snapshot all production VMs for a specific client
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "env:prod AND client:acme"}
    ],
    "snapshotName": "prod-backup-{date}",
    "description": "Production backup",
    "ttlHours": 72
  }' | jq .
```

## Bulk Power Operations

### Start Multiple VMs
```bash
# Start specific VMs
curl -X POST http://localhost:8080/api/v1/vms/power/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "operation": "START",
    "skipIfAlreadyInState": true
  }' | jq .
```

### Shutdown VMs by Pattern
```bash
# Gracefully shutdown all worker nodes
curl -X POST http://localhost:8080/api/v1/vms/power/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-wk-*"}
    ],
    "operation": "SHUTDOWN",
    "timeoutSeconds": 300,
    "maxParallel": 3
  }' | jq .
```

### Force Stop VMs
```bash
# Force stop VMs (use with caution)
curl -X POST http://localhost:8080/api/v1/vms/power/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "env:dev AND NOT always-on"}
    ],
    "operation": "STOP",
    "force": true,
    "dryRun": true
  }' | jq .
```

### Reboot VMs
```bash
# Reboot all VMs in maintenance window
curl -X POST http://localhost:8080/api/v1/vms/power/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "maint-ok"}
    ],
    "operation": "REBOOT",
    "maxParallel": 5
  }' | jq .
```

### Power Operations Reference
- **Operations**: `START`, `STOP`, `SHUTDOWN`, `REBOOT`
- **Options**:
  - `skipIfAlreadyInState`: Skip VMs already in target state (default: true)
  - `force`: Force operation for stop/reboot (default: false)
  - `timeoutSeconds`: Timeout for graceful operations (30-3600, default: 300)
  - `maxParallel`: Concurrent operations (1-20, default: 5)
  - `dryRun`: Preview without executing (default: false)

## Tag Management

### Get All Unique Tags
```bash
curl -X GET http://localhost:8080/api/v1/tags | jq .
```

### Get VMs with Specific Tag
```bash
curl -X GET "http://localhost:8080/api/v1/tags/client-nixz/vms" | jq .
```

### Bulk Tag Operations
```bash
# Add tags to VMs by name pattern
curl -X POST "http://localhost:8080/api/v1/tags/bulk?namePattern=workshop-*" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD", 
    "tags": ["workshop", "env:test"]
  }' | jq .

# Remove tags from specific VMs
curl -X POST "http://localhost:8080/api/v1/tags/bulk?vmIds=8200,8201,8202" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "REMOVE",
    "tags": ["temp"]
  }' | jq .
```

## Scheduler Management

### List All Scheduled Jobs
```bash
curl -X GET http://localhost:8080/api/v1/scheduler/jobs | jq .
```

### Create Scheduled Snapshot Job
```bash
# Daily snapshots at 2 AM with 7-day rotation
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "daily-snapshots",
    "description": "Daily snapshots for workshop VMs",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "description": "Automated daily snapshot",
      "maxSnapshots": "7"
    },
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-*"}
    ]
  }' | jq .
```

### Create Snapshot Cleanup Job
```bash
# Hourly cleanup of expired snapshots
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cleanup-expired-snapshots",
    "description": "Remove snapshots past their TTL",
    "taskType": "snapshot_delete",
    "cronExpression": "0 0 * * * ?",
    "enabled": true,
    "parameters": {
      "checkDescription": "true",
      "safeMode": "true",
      "dryRun": "false"
    },
    "vmSelectors": [
      {"type": "ALL", "value": "*"}
    ]
  }' | jq .
```

### Create Pre-Update Snapshot Job (Manual Trigger)
```bash
# Manual trigger job for pre-update snapshots with 24h TTL
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-snapshots",
    "description": "Create snapshots before system updates",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 0 1 1 ? 2099",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "preupd-{vm}-{datetime}",
      "description": "Pre-update checkpoint",
      "snapshotTTL": "24"
    },
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "maint-ok AND NOT always-on"}
    ]
  }' | jq .
```

### Trigger Job Manually
```bash
# Get job ID first
JOB_ID=$(curl -s http://localhost:8080/api/v1/scheduler/jobs | jq -r '.[] | select(.name == "pre-update-snapshots") | .id')

# Trigger the job
curl -X POST "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID/trigger" | jq .
```

### Update Job
```bash
# Disable a job temporarily
curl -X PUT "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": false
  }' | jq .
```

### Delete Job
```bash
curl -X DELETE "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID" | jq .
```

## Backup Operations

### List All Backups
```bash
curl -X GET http://localhost:8080/api/v1/backups | jq .
```

### Get Retention Candidates
```bash
# Get backups older than 30 days
curl "http://localhost:8080/api/v1/backups/retention-candidates?retentionPolicy=days:30" | jq .

# Get backups to keep only last 5
curl "http://localhost:8080/api/v1/backups/retention-candidates?retentionPolicy=count:5&tags=env:dev" | jq .
```

### Cleanup Old Backups
```bash
# Dry run first
curl -X POST http://localhost:8080/api/v1/backups/cleanup \
  -H "Content-Type: application/json" \
  -d '{
    "retentionPolicy": "days:30",
    "dryRun": true,
    "tags": ["env:dev"]
  }' | jq .
```

## Bulk Backup Operations

### Create Backups by VM IDs
```bash
# Dry run first
curl -X POST http://localhost:8080/api/v1/backups/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "storage": "local",
    "mode": "snapshot",
    "compress": "zstd",
    "notes": "Pre-maintenance backup",
    "dryRun": true
  }' | jq .

# Actual backup creation
curl -X POST http://localhost:8080/api/v1/backups/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "storage": "local",
    "mode": "snapshot",
    "compress": "zstd",
    "notes": "Pre-maintenance backup",
    "maxParallel": 3,
    "dryRun": false
  }' | jq .
```

### Create Backups by Name Pattern
```bash
# Backup all worker nodes
curl -X POST http://localhost:8080/api/v1/backups/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-wk-*"}
    ],
    "storage": "local",
    "mode": "snapshot",
    "compress": "zstd",
    "notes": "Worker nodes backup",
    "maxParallel": 2
  }' | jq .
```

### Create Backups by Tag Expression
```bash
# Backup all production VMs with 30-day TTL
curl -X POST http://localhost:8080/api/v1/backups/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "env:prod AND client:acme"}
    ],
    "storage": "local",
    "mode": "snapshot",
    "compress": "zstd",
    "notes": "Production backup",
    "ttlDays": 30,
    "protectBackup": true,
    "maxParallel": 5
  }' | jq .
```

### Create Backups with Stop Mode
```bash
# Backup VMs that need to be stopped first
curl -X POST http://localhost:8080/api/v1/backups/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "backup:stop-required"}
    ],
    "storage": "local",
    "mode": "stop",
    "compress": "lzo",
    "notes": "Consistent backup with VM stop",
    "mailNotification": "failure"
  }' | jq .
```

### Backup Options Reference
- **Modes**: `snapshot` (default), `suspend`, `stop`
- **Compression**: `zstd` (default), `gzip`, `lzo`, `0` (no compression)
- **Options**:
  - `ttlDays`: Auto-delete backup after N days (1-3653)
  - `removeOlder`: Remove backups older than N days (0-365)
  - `protectBackup`: Protect backup from deletion
  - `mailNotification`: Send email on `always` or `failure`
  - `maxParallel`: Concurrent backup operations (1-20, default: 3)
  - `dryRun`: Preview without executing

## Common Patterns

### VM Selector Types
- `ALL`: Select all VMs
- `VM_IDS`: Comma-separated VM IDs (e.g., "8200,8201,8202")
- `NAME_PATTERN`: Wildcard patterns (e.g., "web-*", "*-prod", "app-?-server")
- `TAG_EXPRESSION`: Boolean expressions (e.g., "env:prod AND client:acme", "backup:daily OR backup:weekly")

### Cron Expression Examples
- `0 0 2 * * ?` - Every day at 2:00 AM
- `0 0 * * * ?` - Every hour
- `0 0 0 * * MON` - Every Monday at midnight
- `0 0/15 * * * ?` - Every 15 minutes
- `0 0 0 1 1 ? 2099` - Never (manual trigger only)

### Placeholder Patterns
- `{vm}` - VM name
- `{vmid}` - VM ID
- `{date}` - Date in YYYYMMDD format
- `{time}` - Time in HHMMSS format
- `{datetime}` - Combined date and time

### TTL Format
TTL is specified in hours (1-8760). When set, it's appended to descriptions as "(TTL: Xh)" and can be processed by the cleanup scheduler.

## VM Migration

### Migrate Single VM
```bash
# Simple migration - will use online migration if VM is running
curl -X POST http://localhost:8080/api/v1/vms/8200/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "targetNode": "hv2"
  }' | jq .

# Migration with offline fallback if online fails
curl -X POST http://localhost:8080/api/v1/vms/8200/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "targetNode": "hv2",
    "allowOfflineMigration": true
  }' | jq .

# Migration with local disks and bandwidth limit
curl -X POST http://localhost:8080/api/v1/vms/8200/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "targetNode": "hv2",
    "withLocalDisks": true,
    "bwlimit": 51200,
    "allowOfflineMigration": true
  }' | jq .

# Force migration with specific storage mapping
curl -X POST http://localhost:8080/api/v1/vms/8200/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "targetNode": "hv2",
    "force": true,
    "targetStorage": "local-zfs",
    "withLocalDisks": true
  }' | jq .
```

### Check Migration Preconditions
```bash
# Check if VM can be migrated to a specific node
curl -X GET "http://localhost:8080/api/v1/vms/8200/migrate/check?target=hv2" | jq .
```

### Get Migration History
```bash
# Get all migrations for a specific VM
curl -X GET http://localhost:8080/api/v1/vms/8200/migrate/history | jq .
```

### Migration Options Reference
- **targetNode**: Target node name (required)
- **allowOfflineMigration**: Allow offline migration if online fails (default: false)
- **withLocalDisks**: Migrate with local disks (default: false)
- **force**: Force migration of VMs with local devices (default: false)
- **bwlimit**: Bandwidth limit in KiB/s
- **targetStorage**: Storage mapping (single ID or mapping string)
- **migrationType**: "secure" (default) or "insecure"
- **migrationNetwork**: CIDR for migration network

### Migration Behavior
1. **Running VMs**: Attempts online migration by default
2. **Stopped VMs**: Performs offline migration
3. **Failed Online**: Returns error unless `allowOfflineMigration=true`
4. **State Recovery**: Automatically restarts VMs that were running before migration
5. **History Tracking**: All migrations are recorded in database for audit

## SSH Key Management

SSH keys can be set during VM creation or updated on existing VMs.

### Set SSH Keys on Existing VM
```bash
# Update SSH keys on an existing VM
curl -X PUT http://localhost:8080/api/v1/vms/300/ssh-keys \
  -H "Content-Type: application/json" \
  -d '{
    "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGLmQqfp8X5DUVxLruBsCmJ7m4mDGcr5V7e2BXMkNPDp user@example.com"
  }'
```

### Multiple SSH Keys
```bash
# Set multiple SSH keys (one per line)
curl -X PUT http://localhost:8080/api/v1/vms/300/ssh-keys \
  -H "Content-Type: application/json" \
  -d '{
    "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGLmQqfp8X5DUVxLruBsCmJ7m4mDGcr5V7e2BXMkNPDp user1@example.com\nssh-rsa AAAAB3NzaC1yc2EAAAA... user2@example.com"
  }'
```