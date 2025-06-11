# Moxxie API Specification

## Overview
Moxxie provides a RESTful API for managing a single Proxmox cluster. Each Moxxie instance manages one location and exposes a uniform API for orchestration by Cafn8.

## Base URL
```
https://moxxie-{instance-id}.internal.example.com/api/v1
```

## Authentication
All endpoints except health checks require authentication via Keycloak OIDC Bearer token.

```
Authorization: Bearer <access_token>
```

## Common Response Formats

### Success Response
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2024-01-10T10:00:00Z",
    "instance": "wsdc1"
  }
}
```

### Error Response
```json
{
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "VM with ID 100 not found",
    "details": { ... }
  },
  "meta": {
    "timestamp": "2024-01-10T10:00:00Z",
    "instance": "wsdc1"
  }
}
```

## Endpoints

### Instance Information

#### GET /api/v1/info
Returns information about this Moxxie instance.

**Response:**
```json
{
  "data": {
    "instanceId": "wsdc1",
    "location": "Amsterdam",
    "provider": "Worldstream",
    "version": "1.0.0",
    "capabilities": ["proxmox", "ceph", "vlans"],
    "proxmoxVersion": "8.1.3"
  }
}
```

### Cluster Discovery

#### POST /api/v1/cluster/discover
Discovers and returns current cluster configuration.

**Response:**
```json
{
  "data": {
    "nodes": [
      {
        "name": "pve1",
        "status": "online",
        "cpu": {
          "total": 32,
          "used": 16,
          "model": "Intel Xeon E5-2680"
        },
        "memory": {
          "totalGB": 128,
          "usedGB": 64
        }
      }
    ],
    "storage": [
      {
        "name": "local-lvm",
        "type": "lvm",
        "totalGB": 1000,
        "availableGB": 600
      }
    ],
    "networks": [
      {
        "name": "vmbr0",
        "type": "bridge",
        "cidr": "10.0.0.0/24"
      }
    ]
  }
}
```

### Node Management

#### GET /api/v1/nodes
List all nodes in the cluster.

#### GET /api/v1/nodes/{nodeId}
Get detailed information about a specific node.

#### GET /api/v1/nodes/{nodeId}/status
Get current status and resource usage of a node.

### VM Management

#### GET /api/v1/vms
List all VMs in the cluster.

**Query Parameters:**
- `node`: Filter by node name
- `status`: Filter by status (running, stopped)
- `limit`: Number of results (default: 100)
- `offset`: Pagination offset

#### GET /api/v1/vms/{vmId}
Get detailed information about a specific VM.

#### POST /api/v1/vms
Create a new VM.

**Request Body:**
```json
{
  "name": "web-server-01",
  "node": "pve1",
  "template": "debian-12",
  "cores": 4,
  "memoryMB": 8192,
  "diskGB": 100,
  "network": {
    "bridge": "vmbr0",
    "vlan": 100
  },
  "startOnBoot": true,
  "metadata": {
    "owner": "team-web",
    "purpose": "production web server"
  }
}
```

#### DELETE /api/v1/vms/{vmId}
Delete a VM.

**Query Parameters:**
- `purge`: Also remove all backups (default: false)

### Resource Management

#### GET /api/v1/resources/available
Get available resources for VM placement.

**Query Parameters:**
- `cpu_min`: Minimum CPU cores required
- `memory_min`: Minimum memory in GB
- `disk_min`: Minimum disk space in GB
- `network`: Required network/VLAN

#### POST /api/v1/placement/validate
Validate if resources can be allocated.

**Request Body:**
```json
{
  "requirements": {
    "cpu": 4,
    "memoryGB": 16,
    "diskGB": 100,
    "networks": ["vlan100", "vlan200"]
  },
  "preferences": {
    "node": "pve1",
    "storage": "local-lvm"
  }
}
```

### Migration Support

#### POST /api/v1/migrations/export/{vmId}
Export a VM for migration.

**Response:**
```json
{
  "data": {
    "migrationId": "mig-123",
    "vmId": "100",
    "format": "vzdump",
    "compression": "zstd",
    "sizeBytes": 10737418240,
    "checksum": "sha256:abc123...",
    "downloadUrl": "/api/v1/migrations/mig-123/download",
    "expiresAt": "2024-01-10T12:00:00Z"
  }
}
```

#### GET /api/v1/migrations/{migrationId}/download
Download the migration bundle.

#### POST /api/v1/migrations/import
Import a VM from migration bundle.

**Request:** multipart/form-data with backup file

### Operations

#### POST /api/v1/operations/bulk
Execute multiple operations in a transaction.

**Request Body:**
```json
{
  "operations": [
    {
      "id": "op1",
      "type": "CREATE_VM",
      "params": { ... }
    },
    {
      "id": "op2", 
      "type": "CONFIGURE_NETWORK",
      "params": { ... },
      "dependsOn": ["op1"]
    }
  ]
}
```

#### GET /api/v1/operations/{operationId}
Get status of an async operation.

### Health & Monitoring

#### GET /q/health/live
Liveness probe - returns 200 if service is alive.

#### GET /q/health/ready
Readiness probe - returns 200 if service is ready to handle requests.

#### GET /q/metrics
Prometheus metrics endpoint.

## Error Codes

| Code | Description |
|------|-------------|
| `RESOURCE_NOT_FOUND` | Requested resource does not exist |
| `RESOURCE_CONFLICT` | Resource already exists or conflict |
| `INVALID_REQUEST` | Request validation failed |
| `PROXMOX_ERROR` | Error from Proxmox API |
| `QUOTA_EXCEEDED` | Resource limits exceeded |
| `OPERATION_FAILED` | Operation could not be completed |
| `AUTHENTICATION_REQUIRED` | Missing or invalid auth token |
| `PERMISSION_DENIED` | Insufficient permissions |

## Rate Limiting

API requests are rate limited per authenticated user:
- 1000 requests per hour for read operations
- 100 requests per hour for write operations

Rate limit headers:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1641816000
```