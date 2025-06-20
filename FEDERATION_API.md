# Moxxie Federation API Documentation

This document describes the federation-ready API endpoints implemented in Moxxie as part of issue #53.

## Overview

These endpoints provide resource visibility for federation management by Cafn8. They expose current capacity, utilization, and capabilities of the Moxxie instance.

## Authentication

All endpoints require authentication using the same mechanism as other Moxxie APIs.

## Endpoints

### 1. GET /api/v1/federation/capacity

Returns available capacity for VM provisioning.

**Response:**
```json
{
  "location": {
    "provider": "proxmox",
    "region": "nl-west-1",
    "datacenter": "wsdc1"
  },
  "capacity": {
    "vcpus": {
      "total": 1000,
      "available": 750,
      "reserved": 100
    },
    "memory_gb": {
      "total": 4000,
      "available": 3000,
      "reserved": 600
    },
    "storage_gb": {
      "total": 50000,
      "available": 35000,
      "reserved": 5000
    }
  },
  "largest_possible_vm": {
    "vcpus": 64,
    "memory_gb": 256,
    "storage_gb": 2000
  },
  "timestamp": "2024-01-15T10:00:00Z"
}
```

### 2. GET /api/v1/federation/utilization

Returns current resource utilization metrics.

**Response:**
```json
{
  "location": {
    "provider": "proxmox",
    "region": "nl-west-1",
    "datacenter": "wsdc1"
  },
  "utilization": {
    "vcpu_percent": 25.0,
    "memory_percent": 30.5,
    "storage_percent": 40.2
  },
  "vm_count": {
    "total": 150,
    "running": 140,
    "stopped": 10
  },
  "trends": {
    "vcpu_trend_1h": "+0.0%",
    "memory_trend_1h": "+0.0%"
  },
  "timestamp": "2024-01-15T10:00:00Z"
}
```

### 3. GET /api/v1/federation/capabilities

Returns location capabilities and constraints.

**Response:**
```json
{
  "location": {
    "provider": "proxmox",
    "region": "nl-west-1",
    "datacenter": "wsdc1"
  },
  "capabilities": {
    "vm_types": ["general", "compute", "memory"],
    "max_vcpus_per_vm": 128,
    "max_memory_gb_per_vm": 512,
    "max_storage_gb_per_vm": 10000,
    "features": [
      "snapshots",
      "live_migration",
      "nested_virtualization",
      "cloud_init",
      "qemu_agent",
      "vnc_console",
      "spice_console"
    ],
    "networking": {
      "ipv6_support": true,
      "private_networks": true,
      "floating_ips": false
    }
  },
  "constraints": {
    "compliance": ["gdpr"],
    "certifications": []
  }
}
```

### 4. POST /api/v1/federation/estimate

Estimates cost and checks feasibility for VM provisioning.

**Request:**
```json
{
  "vcpus": 4,
  "memory_gb": 16,
  "storage_gb": 100,
  "duration_hours": 720
}
```

**Response:**
```json
{
  "feasible": true,
  "estimated_cost": {
    "amount": 125.50,
    "currency": "USD",
    "period": "monthly"
  },
  "availability": {
    "immediate": true,
    "wait_time_minutes": 0
  }
}
```

## Configuration

Resource management behavior can be configured via application.properties:

```properties
# CPU overcommit ratio (default: 4:1)
moxxie.resources.cpu.overcommit-ratio=4.0

# Memory overcommit ratio (default: 1:1, no overcommit)
moxxie.resources.memory.overcommit-ratio=1.0

# Storage overprovision ratio for thin provisioning (default: 1.5:1)
moxxie.resources.storage.overprovision-ratio=1.5

# Resource reserves for system overhead (percentages)
moxxie.resources.cpu.reserve-percent=10
moxxie.resources.memory.reserve-percent=15
moxxie.resources.storage.reserve-percent=10
```

## Implementation Notes

1. **Resource Calculations**: Available resources consider overcommit ratios and system reserves
2. **Caching**: Resource data is cached for 5 minutes to reduce load on Proxmox API
3. **Multi-Node Support**: Resources are aggregated across all nodes in the cluster
4. **Cost Estimation**: Currently uses placeholder pricing; integrate with actual pricing model

## Future Enhancements

- Historical trend data (requires metrics storage)
- Real-time resource pressure alerts
- Integration with actual cost models
- Support for resource reservations