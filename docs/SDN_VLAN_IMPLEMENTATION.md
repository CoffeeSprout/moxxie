# SDN VLAN Management Implementation

## Overview
Implemented comprehensive SDN (Software Defined Networking) support in Moxxie for VLAN management in multi-tenant environments. This feature enables explicit VLAN allocation and tracking per client/project with strong network isolation.

## What Was Implemented

### 1. SDN API Client Extensions
- **Location**: `ProxmoxClient.java`
- Added methods for:
  - Listing SDN zones
  - Managing VNets (create, list, delete)
  - Applying SDN configuration

### 2. SDN Service Layer
- **Location**: `SDNService.java`
- Features:
  - Manual VLAN allocation with explicit assignment
  - Client-based VLAN tracking
  - VNet lifecycle management
  - Configurable naming patterns

### 3. REST API Endpoints
- **Location**: `SDNResource.java`
- Endpoints:
  - `GET /api/v1/sdn/zones` - List SDN zones
  - `GET /api/v1/sdn/vnets` - List VNets
  - `GET /api/v1/sdn/vlan-assignments` - List VLAN assignments (NEW)
  - `POST /api/v1/sdn/vnets` - Create VNet with explicit VLAN
  - `DELETE /api/v1/sdn/vnets/{vnetId}` - Delete VNet
  - `GET /api/v1/sdn/clients/{clientId}/vlan` - Get client VLAN
  - `POST /api/v1/sdn/apply` - Apply SDN config

### 4. VM Creation Updates
- **Location**: `VMResource.java`, `CreateVMRequestDTO.java`
- Added `clientId` and `project` fields for future use
- VMs use manually specified VLANs only
- No automatic VLAN assignment on VM creation

### 5. Configuration Properties
- **Location**: `application.properties`
```properties
moxxie.sdn.enabled=false
moxxie.sdn.default-zone=localzone
moxxie.sdn.vlan-range-start=100
moxxie.sdn.vlan-range-end=4000
moxxie.sdn.auto-create-vnets=false
moxxie.sdn.vnet-naming-pattern={client}-{project}
moxxie.sdn.apply-on-change=true
```

### 6. Database Schema
- **Location**: `sdn_schema.sql`
- Tables:
  - `vlan_allocations` - Track client VLAN assignments
  - `sdn_audit_log` - Audit all SDN operations
  - `vnet_configurations` - Store VNet metadata

### 7. Safe Mode Integration
- All SDN write operations respect Safe Mode
- Proper annotations on create/delete operations

### 8. Unit Tests
- **Location**: `SDNServiceTest.java`, `SDNResourceTest.java`
- Comprehensive test coverage for:
  - VLAN allocation algorithm
  - VNet creation/deletion
  - REST API endpoints
  - Error handling

## Usage Examples

### View VLAN Assignments
```bash
# List all VLANs in range 100-200
GET /api/v1/sdn/vlan-assignments?rangeStart=100&rangeEnd=200

# List only allocated VLANs
GET /api/v1/sdn/vlan-assignments?rangeStart=100&rangeEnd=110&allocatedOnly=true
```

Response shows which VLANs are in use:
```json
[
  {
    "vlanTag": 101,
    "clientId": "client1",
    "vnetIds": ["client1-webapp", "client1-api"],
    "status": "allocated",
    "description": null
  },
  {
    "vlanTag": 102,
    "clientId": null,
    "vnetIds": [],
    "status": "available",
    "description": null
  }
]
```

### List client networks
```bash
GET /api/v1/sdn/vnets?client=client1
```

### Create VNet with explicit VLAN
```json
POST /api/v1/sdn/vnets
{
  "clientId": "client2",
  "project": "api",
  "zone": "localzone",
  "vlanTag": 105
}
```

### Create VM with manual VLAN
```json
POST /api/v1/vms
{
  "name": "client1-web-01",
  "node": "hv6",
  "cores": 2,
  "memoryMB": 4096,
  "network": {
    "bridge": "vmbr0",
    "vlan": 101
  }
}
```

## Benefits
1. **VLAN Visibility**: See which VLANs are allocated and to which SDN VNets
2. **Manual Control**: Explicit VLAN assignment prevents conflicts
3. **Multi-Tenancy**: Strong network boundaries between clients
4. **Scalability**: Supports thousands of VLANs/clients
5. **Auditability**: Complete tracking of network allocations
6. **Integration**: Works seamlessly with existing Proxmox SDN

## Configuration Required
To enable SDN functionality:
1. Set `MOXXIE_SDN_ENABLED=true`
2. Configure Proxmox SDN zones
3. Set appropriate VLAN range
4. Apply database schema for persistence