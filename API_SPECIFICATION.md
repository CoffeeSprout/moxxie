# Moxxie API Specification

This document provides a comprehensive specification of all Moxxie REST API endpoints, including request/response schemas, query parameters, and data models.

## Base URL

```
http://localhost:8080/api/v1
```

## Table of Contents

1. [Virtual Machines](#virtual-machines)
2. [Snapshots](#snapshots)
3. [Backups](#backups)
4. [Tags](#tags)
5. [Bulk Operations](#bulk-operations)
6. [Cluster Management](#cluster-management)
7. [Node Management](#node-management)
8. [Storage](#storage)
9. [Network](#network)
10. [Migration](#migration)
11. [Tasks](#tasks)
12. [Scheduler](#scheduler)
13. [Health & Info](#health--info)
14. [Console Access](#console-access)
15. [Administration](#administration)

## Common Response Formats

### Error Response
```json
{
  "error": "string",
  "message": "string",
  "timestamp": "2024-06-21T10:30:00Z"
}
```

### Bulk Operation Response
```json
{
  "totalTargets": 5,
  "successful": 3,
  "failed": 2,
  "results": [
    {
      "vmId": 101,
      "success": true,
      "message": "Operation completed"
    },
    {
      "vmId": 102,
      "success": false,
      "error": "VM is locked"
    }
  ]
}
```

## Virtual Machines

### List VMs
```
GET /vms
```

Query Parameters:
- `node` (string, optional): Filter by node name
- `pool` (string, optional): Filter by resource pool
- `tags` (string, optional): Comma-separated list of tags (AND logic)
- `client` (string, optional): Filter by client tag
- `status` (string, optional): Filter by status (running, stopped)
- `vmIds` (string, optional): Comma-separated list of VM IDs
- `namePattern` (string, optional): Filter by name pattern (supports wildcards)
- `limit` (integer, optional): Max results to return
- `offset` (integer, optional): Pagination offset

Response:
```json
[
  {
    "vmid": 101,
    "name": "web-server-01",
    "node": "proxmox-01",
    "status": "running",
    "mem": 4294967296,
    "maxmem": 4294967296,
    "disk": 21474836480,
    "maxdisk": 53687091200,
    "cpu": 0.05,
    "maxcpu": 2,
    "uptime": 3600,
    "netin": 1024000,
    "netout": 2048000,
    "diskread": 1073741824,
    "diskwrite": 536870912,
    "template": false,
    "tags": ["env-prod", "client-acme", "always-on"]
  }
]
```

### Get VM Details
```
GET /vms/{vmId}
```

Response: Same as single VM in list response

### Get VM Extended Details
```
GET /vms/{vmId}/detail
```

Response:
```json
{
  "vm": { /* VM object */ },
  "config": {
    "name": "web-server-01",
    "cores": 2,
    "memory": 4096,
    "boot": "order=scsi0;ide2;net0",
    "scsihw": "virtio-scsi-single",
    "ostype": "l26",
    "ide2": "none,media=cdrom",
    "net0": "virtio=AA:BB:CC:DD:EE:FF,bridge=vmbr0",
    "scsi0": "local-zfs:vm-101-disk-0,size=50G"
  },
  "snapshots": [
    {
      "name": "backup-20240621",
      "snaptime": 1719230400,
      "description": "Pre-update backup"
    }
  ],
  "backups": [
    {
      "volid": "local:backup/vzdump-qemu-101-2024_06_21-10_00_00.vma.zst",
      "size": 2147483648,
      "ctime": 1719230400,
      "notes": "Daily backup"
    }
  ]
}
```

### Create VM
```
POST /vms
```

Request Body:
```json
{
  "node": "proxmox-01",
  "vmId": 101,
  "name": "web-server-01",
  "cores": 2,
  "memory": 4096,
  "disks": [
    {
      "size": "50G",
      "storage": "local-zfs",
      "interface": "scsi",
      "index": 0
    }
  ],
  "networks": [
    {
      "bridge": "vmbr0",
      "model": "virtio",
      "index": 0,
      "vlan": 100,
      "firewall": true
    }
  ],
  "osType": "l26",
  "bootOrder": "scsi0,ide2,net0",
  "tags": ["env-prod", "client-acme"],
  "start": true
}
```

Response:
```json
{
  "vmId": 101,
  "task": "UPID:proxmox-01:00001234:00ABCDEF:12345678:qmcreate:101:root@pam:"
}
```

### Create Cloud-Init VM
```
POST /vms/cloud-init
```

Request Body:
```json
{
  "node": "proxmox-01",
  "vmId": 102,
  "name": "cloud-vm-01",
  "cores": 2,
  "memory": 4096,
  "imageSource": "local-zfs:base-9002-disk-0",
  "targetStorage": "local-zfs",
  "diskSize": "50G",
  "networks": [
    {
      "bridge": "vmbr0",
      "model": "virtio",
      "index": 0
    }
  ],
  "cloudInit": {
    "user": "ubuntu",
    "password": "secure-password",
    "sshKeys": "ssh-ed25519 AAAAC3...",
    "ipAddress": "192.168.1.100/24",
    "gateway": "192.168.1.1",
    "nameservers": "8.8.8.8,8.8.4.4"
  },
  "tags": ["env-dev", "cloud-init"],
  "start": true
}
```

### Delete VM
```
DELETE /vms/{vmId}
```

Query Parameters:
- `purge` (boolean, optional): Also remove from backup storage

Response:
```json
{
  "task": "UPID:proxmox-01:00001234:00ABCDEF:12345678:qmdestroy:101:root@pam:"
}
```

### Update VM SSH Keys
```
PUT /vms/{vmId}/ssh-keys
```

Request Body:
```json
{
  "sshKeys": "ssh-ed25519 AAAAC3... user@host"
}
```

### VM Power Operations

#### Start VM
```
POST /vms/{vmId}/start
```

#### Stop VM
```
POST /vms/{vmId}/stop
```

Query Parameters:
- `force` (boolean, optional): Force stop

#### Shutdown VM
```
POST /vms/{vmId}/shutdown
```

Query Parameters:
- `forceStop` (boolean, optional): Force if guest agent not responding
- `timeout` (integer, optional): Timeout in seconds

#### Reboot VM
```
POST /vms/{vmId}/reboot
```

#### Reset VM
```
POST /vms/{vmId}/reset
```

#### Suspend VM
```
POST /vms/{vmId}/suspend
```

#### Resume VM
```
POST /vms/{vmId}/resume
```

### VM Debug Info
```
GET /vms/{vmId}/debug
```

Response:
```json
{
  "vmId": 101,
  "node": "proxmox-01",
  "config": { /* Full VM config */ },
  "status": { /* Current status */ },
  "haStatus": { /* HA configuration */ },
  "replication": { /* Replication status */ },
  "blockStatus": { /* Block device status */ }
}
```

## Snapshots

### List Snapshots
```
GET /vms/{vmId}/snapshots
```

Response:
```json
[
  {
    "name": "backup-20240621",
    "snaptime": 1719230400,
    "description": "Pre-update backup (TTL: 24h)",
    "parent": "current",
    "vmstate": false
  }
]
```

### Create Snapshot
```
POST /vms/{vmId}/snapshots
```

Request Body:
```json
{
  "name": "backup-20240621",
  "description": "Pre-update backup",
  "ttlHours": 24,
  "includeVMState": false
}
```

### Delete Snapshot
```
DELETE /vms/{vmId}/snapshots/{snapshotName}
```

### Rollback to Snapshot
```
POST /vms/{vmId}/snapshots/{snapshotName}/rollback
```

## Backups

### List VM Backups
```
GET /vms/{vmId}/backups
```

Response:
```json
[
  {
    "volid": "local:backup/vzdump-qemu-101-2024_06_21-10_00_00.vma.zst",
    "content": "backup",
    "ctime": 1719230400,
    "format": "vma.zst",
    "size": 2147483648,
    "notes": "Daily backup",
    "protected": false,
    "verification": {
      "state": "ok",
      "upid": "UPID:..."
    }
  }
]
```

### List All Backups
```
GET /backups
```

Query Parameters:
- `node` (string, optional): Filter by node
- `storage` (string, optional): Filter by storage
- `vmId` (integer, optional): Filter by VM ID

### Get Backup Config
```
GET /backups/{storageId}/{backupId}/config
```

### Create Backup
```
POST /vms/{vmId}/backup
```

Request Body:
```json
{
  "storage": "local",
  "mode": "snapshot",
  "compress": "zstd",
  "notes": "Manual backup before maintenance",
  "protected": true,
  "skipLock": false,
  "notificationMode": "auto",
  "removeOlder": 7
}
```

### Delete Backup
```
DELETE /backups/{storageId}/{backupId}
```

## Tags

### List All Tags
```
GET /tags
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
    "name": "client-acme",
    "count": 8,
    "category": "client"
  }
]
```

### Get VMs by Tag
```
GET /tags/{tagName}/vms
```

Response: Array of VM objects

### Add Tags to VM
```
POST /vms/{vmId}/tags
```

Request Body:
```json
{
  "tags": ["env-prod", "critical"]
}
```

### Remove Tags from VM
```
DELETE /vms/{vmId}/tags
```

Request Body:
```json
{
  "tags": ["old-tag"]
}
```

### Update VM Tags
```
PUT /vms/{vmId}/tags
```

Request Body:
```json
{
  "tags": ["env-prod", "client-acme", "always-on"]
}
```

### Bulk Tag Operations
```
POST /tags/bulk
```

Query Parameters:
- `vmIds` (string, optional): Comma-separated VM IDs
- `namePattern` (string, optional): VM name pattern
- `node` (string, optional): Filter by node
- `pool` (string, optional): Filter by pool

Request Body:
```json
{
  "action": "ADD",
  "tags": ["env-prod", "maint-ok"]
}
```

Actions: `ADD`, `REMOVE`, `REPLACE`

## Bulk Operations

### Bulk Snapshot
```
POST /bulk/snapshots
```

Request Body:
```json
{
  "vmIds": [101, 102, 103],
  "snapshotName": "maintenance-{date}",
  "description": "Pre-maintenance snapshot",
  "ttlHours": 48,
  "includeVMState": false,
  "dryRun": false
}
```

### Bulk Power Operations
```
POST /bulk/power/{action}
```

Actions: `start`, `stop`, `shutdown`, `reboot`, `suspend`, `resume`

Request Body:
```json
{
  "vmIds": [101, 102, 103],
  "force": false,
  "timeout": 60,
  "parallel": true,
  "dryRun": false
}
```

### Bulk Backup
```
POST /bulk/backup
```

Request Body:
```json
{
  "vmIds": [101, 102, 103],
  "storage": "backup-nas",
  "mode": "snapshot",
  "compress": "zstd",
  "notes": "Weekly backup",
  "protected": false,
  "dryRun": false
}
```

## Cluster Management

### Get Cluster Status
```
GET /cluster/status
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
    }
  ]
}
```

### Get Cluster Resources
```
GET /cluster/resources
```

Query Parameters:
- `type` (string, optional): Filter by type (vm, storage, node)

Response:
```json
[
  {
    "id": "qemu/101",
    "type": "qemu",
    "node": "proxmox-01",
    "status": "running",
    "name": "web-server-01",
    "cpu": 0.05,
    "maxcpu": 2,
    "mem": 2147483648,
    "maxmem": 4294967296
  }
]
```

### Join Cluster
```
POST /cluster/join
```

Request Body:
```json
{
  "targetIp": "192.168.1.10",
  "targetPassword": "root-password",
  "fingerprint": "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
  "force": false
}
```

## Node Management

### List Nodes
```
GET /nodes
```

Response:
```json
[
  {
    "node": "proxmox-01",
    "status": "online",
    "cpu": 0.15,
    "maxcpu": 32,
    "mem": 8589934592,
    "maxmem": 137438953472,
    "disk": 107374182400,
    "maxdisk": 1099511627776,
    "uptime": 864000,
    "level": "",
    "ssl_fingerprint": "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
  }
]
```

### Get Node Details
```
GET /nodes/{nodeName}
```

### Get Node Services
```
GET /nodes/{nodeName}/services
```

### Execute Command on Node
```
POST /nodes/{nodeName}/execute
```

Request Body:
```json
{
  "command": "systemctl status pve-cluster"
}
```

## Storage

### List Storage
```
GET /storage
```

Query Parameters:
- `node` (string, optional): Filter by node
- `content` (string, optional): Filter by content type
- `enabled` (boolean, optional): Filter enabled storages

Response:
```json
[
  {
    "storage": "local-zfs",
    "type": "zfspool",
    "content": ["images", "rootdir"],
    "active": true,
    "enabled": true,
    "shared": false,
    "total": 1099511627776,
    "used": 214748364800,
    "available": 884763263000,
    "used_fraction": 0.195
  }
]
```

### Get Storage Details
```
GET /storage/{storageId}
```

### Upload ISO/Image
```
POST /storage/{storageId}/upload
```

Multipart form data with file upload

## Network

### List Networks
```
GET /networks
```

Query Parameters:
- `node` (string, optional): Filter by node
- `type` (string, optional): Filter by type (bridge, bond, vlan)

Response:
```json
[
  {
    "iface": "vmbr0",
    "type": "bridge",
    "active": true,
    "autostart": true,
    "bridge_ports": "enp1s0",
    "bridge_stp": "off",
    "bridge_fd": 0,
    "address": "192.168.1.10",
    "netmask": "255.255.255.0",
    "gateway": "192.168.1.1"
  }
]
```

### List SDN VNets
```
GET /sdn/vnets
```

### List SDN Zones
```
GET /sdn/zones
```

## Migration

### Migrate VM
```
POST /vms/{vmId}/migrate
```

Request Body:
```json
{
  "targetNode": "proxmox-02",
  "online": true,
  "withLocalDisks": false,
  "targetStorage": "shared-storage",
  "force": false
}
```

Response:
```json
{
  "task": "UPID:proxmox-01:00001234:00ABCDEF:12345678:qmigrate:101:root@pam:"
}
```

### Get Migration Status
```
GET /vms/{vmId}/migrate/status
```

Response:
```json
{
  "running": true,
  "progress": 0.75,
  "status": "drive-scsi0: Transferring data",
  "startTime": "2024-06-21T10:00:00Z",
  "estimatedCompletion": "2024-06-21T10:15:00Z"
}
```

## Tasks

### Get Task Status
```
GET /tasks/{upid}
```

Response:
```json
{
  "upid": "UPID:proxmox-01:00001234:00ABCDEF:12345678:qmcreate:101:root@pam:",
  "node": "proxmox-01",
  "pid": 4660,
  "pstart": 11534591,
  "starttime": 1719230400,
  "type": "qmcreate",
  "id": "101",
  "user": "root@pam",
  "status": "running",
  "exitstatus": null
}
```

### List Tasks
```
GET /tasks
```

Query Parameters:
- `node` (string, optional): Filter by node
- `start` (integer, optional): Start index
- `limit` (integer, optional): Max results
- `userfilter` (string, optional): Filter by user
- `typefilter` (string, optional): Filter by type
- `statusfilter` (string, optional): Filter by status
- `since` (integer, optional): Unix timestamp
- `until` (integer, optional): Unix timestamp

### Stop Task
```
DELETE /tasks/{upid}
```

## Scheduler

### List Scheduled Jobs
```
GET /scheduler/jobs
```

Response:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "daily-backup",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "lastRun": "2024-06-21T02:00:00Z",
    "nextRun": "2024-06-22T02:00:00Z",
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "maxSnapshots": "7"
    },
    "vmSelectors": [
      {
        "type": "TAG_EXPRESSION",
        "value": "env-prod AND backup-daily"
      }
    ]
  }
]
```

### Create Scheduled Job
```
POST /scheduler/jobs
```

Request Body:
```json
{
  "name": "daily-snapshots",
  "taskType": "snapshot_create",
  "cronExpression": "0 0 2 * * ?",
  "enabled": true,
  "parameters": {
    "snapshotNamePattern": "auto-{vm}-{date}",
    "description": "Automated daily snapshot",
    "maxSnapshots": "7"
  },
  "vmSelectors": [
    {
      "type": "TAG_EXPRESSION",
      "value": "env-prod AND NOT always-on"
    }
  ]
}
```

Task Types:
- `snapshot_create`: Create VM snapshots
- `snapshot_delete`: Delete old snapshots
- `backup_create`: Create VM backups
- `power_schedule`: Schedule VM power operations
- `test_task`: Test task for verification

VM Selector Types:
- `ALL`: All VMs
- `VMID`: Specific VM ID
- `VMID_LIST`: List of VM IDs
- `TAG`: VMs with specific tag
- `TAG_EXPRESSION`: Complex tag expressions
- `NAME_PATTERN`: VM name pattern

### Update Scheduled Job
```
PUT /scheduler/jobs/{jobId}
```

### Delete Scheduled Job
```
DELETE /scheduler/jobs/{jobId}
```

### Trigger Job Manually
```
POST /scheduler/jobs/{jobId}/trigger
```

### Get Job History
```
GET /scheduler/jobs/{jobId}/history
```

Query Parameters:
- `limit` (integer, optional): Max results
- `offset` (integer, optional): Pagination offset

## Health & Info

### Health Check
```
GET /health
```

Response:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Proxmox API",
      "status": "UP",
      "data": {
        "cluster": "production",
        "version": "8.0.3"
      }
    },
    {
      "name": "Database",
      "status": "UP"
    }
  ]
}
```

### Liveness Check
```
GET /health/live
```

### Readiness Check
```
GET /health/ready
```

### API Info
```
GET /info
```

Response:
```json
{
  "application": "Moxxie",
  "version": "1.0.0",
  "buildTime": "2024-06-21T10:00:00Z",
  "proxmoxVersion": "8.0.3",
  "features": {
    "scheduler": true,
    "migration": true,
    "sdn": true,
    "cloudInit": true
  }
}
```

## Console Access

### Get Console URL
```
GET /vms/{vmId}/console
```

Query Parameters:
- `type` (string, optional): Console type (novnc, spice, xterm.js)

Response:
```json
{
  "url": "https://proxmox-01:8006/?console=kvm&novnc=1&vmid=101&vmname=web-server-01&node=proxmox-01",
  "ticket": "PVEVNC:1234567890ABCDEF",
  "port": 5900,
  "cert": "-----BEGIN CERTIFICATE-----..."
}
```

### Get Console WebSocket
```
GET /vms/{vmId}/console/websocket
```

Returns WebSocket upgrade for console connection

## Administration

### Get Audit Log
```
GET /admin/audit
```

Query Parameters:
- `start` (string, optional): Start date (ISO format)
- `end` (string, optional): End date (ISO format)
- `user` (string, optional): Filter by user
- `operation` (string, optional): Filter by operation type
- `limit` (integer, optional): Max results
- `offset` (integer, optional): Pagination offset

Response:
```json
[
  {
    "timestamp": "2024-06-21T10:00:00Z",
    "user": "admin@pam",
    "operation": "VM_CREATE",
    "resourceId": "101",
    "details": {
      "node": "proxmox-01",
      "name": "web-server-01"
    },
    "success": true
  }
]
```

### Safe Mode Status
```
GET /admin/safe-mode
```

Response:
```json
{
  "enabled": true,
  "reason": "Maintenance window",
  "blockedOperations": ["VM_DELETE", "VM_MIGRATE"],
  "since": "2024-06-21T10:00:00Z"
}
```

### Enable/Disable Safe Mode
```
POST /admin/safe-mode
```

Request Body:
```json
{
  "enabled": true,
  "reason": "System maintenance"
}
```

## Data Models

### VM Response Model
```typescript
interface VMResponse {
  vmid: number;
  name: string;
  node: string;
  type: "qemu" | "lxc";
  status: "running" | "stopped" | "paused";
  cpu: number;
  cpus: number;
  mem: number;
  maxmem: number;
  disk: number;
  maxdisk: number;
  netin: number;
  netout: number;
  uptime: number;
  template: boolean;
  tags: string[];
  lock?: string;
  hastate?: string;
}
```

### Task Response Model
```typescript
interface TaskResponse {
  upid: string;
  node: string;
  pid: number;
  pstart: number;
  starttime: number;
  type: string;
  id: string;
  user: string;
  status: "running" | "stopped" | "OK" | "Error";
  exitstatus?: string;
}
```

### Storage Response Model
```typescript
interface StorageResponse {
  storage: string;
  type: string;
  content: string[];
  active: boolean;
  enabled: boolean;
  shared: boolean;
  total: number;
  used: number;
  available: number;
  used_fraction: number;
}
```

### Error Response Model
```typescript
interface ErrorResponse {
  error: string;
  message: string;
  timestamp: string;
  details?: Record<string, any>;
  fieldErrors?: Array<{
    field: string;
    message: string;
  }>;
}
```

## Authentication

Currently, Moxxie does not implement authentication at the API level. The application authenticates with Proxmox using credentials configured in `application.properties`.

Future versions will support:
- API key authentication
- OAuth2/OIDC integration
- Role-based access control (RBAC)

## Rate Limiting

Currently not implemented. Future versions will support configurable rate limits per endpoint.

## Versioning

The API uses URL-based versioning. The current version is `v1`. When breaking changes are introduced, a new version (`v2`) will be created while maintaining backward compatibility.

## WebSocket Endpoints

- `/api/v1/vms/{vmId}/console/websocket` - VM console access
- `/api/v1/tasks/{upid}/log/websocket` - Real-time task log streaming

## Notes for UI Development

1. **Error Handling**: All endpoints return consistent error responses with appropriate HTTP status codes
2. **Async Operations**: Most VM operations return a task UPID that should be polled for completion
3. **Tag Format**: Use dash-separated tags (e.g., `env-prod`, not `env:prod`)
4. **Pagination**: List endpoints support `limit` and `offset` parameters
5. **Filtering**: Most list endpoints support multiple filter parameters that can be combined
6. **Bulk Operations**: Support dry-run mode for testing before execution
7. **WebSocket**: Console and log streaming require WebSocket support

## Common HTTP Status Codes

- `200 OK`: Successful GET, PUT
- `201 Created`: Successful POST creating new resource
- `202 Accepted`: Async operation started
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Invalid request parameters
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource already exists
- `500 Internal Server Error`: Server error
- `503 Service Unavailable`: Proxmox API unavailable