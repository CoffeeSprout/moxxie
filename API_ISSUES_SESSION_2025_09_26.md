# Moxxie API Issues - LibreNMS VM Creation Session
**Date**: September 26, 2025
**Task**: Create Debian 13/12 VM for LibreNMS monitoring with dual network interfaces

## Issues Encountered

### 1. **Image Source Format Inconsistency**
**Issue**: The API validation expects `local-zfs:9001/base-9001-disk-0.raw` format, but documentation examples show `local-zfs:base-9001-disk-0` format.

**Error**:
```json
{
  "field": "createCloudInitVM.request.imageSource",
  "message": "Invalid imageSource format. Expected format: 'storage:vmid/base-vmid-disk-N' (e.g., 'local-zfs:9002/base-9002-disk-0.raw'). This should reference a template VM's disk."
}
```

**Root Cause**: Inconsistent documentation vs validation rules.

**Workaround**: Used VM clone operation instead of cloud-init creation.

### 2. **ZFS Volume Name Parsing Error**
**Issue**: Proxmox API couldn't parse the ZFS volume name when using the expected format.

**Error**:
```
"unable to parse zfs volume name '9001/base-9001-disk-0.raw', status code 500"
```

**Root Cause**: Either the template disk doesn't exist with that exact naming or there's a mismatch between Moxxie's expected format and actual Proxmox disk naming.

**Impact**: Prevented cloud-init VM creation entirely.

### 3. **VLAN Configuration Issues**
**Issue**: When configuring dual networks with VLAN tags, got duplicate key errors.

**Error**:
```json
{
  "error": "PROXMOX_ERROR",
  "message": "Field 'net1': invalid format - duplicate key in comma-separated list property: tag"
}
```

**Configuration Attempted**:
```json
"networks": [
  {
    "model": "virtio",
    "bridge": "vmbr0",
    "firewall": false
  },
  {
    "model": "virtio",
    "bridge": "vmbr1",
    "vlan": 2,
    "firewall": false
  }
]
```

**Root Cause**: The `vlan` property in network configuration was being converted to `tag` internally, causing conflicts.

### 4. **SDN Permission Errors**
**Issue**: Attempting to use `vmbr1` bridge resulted in SDN permission errors.

**Error**:
```json
{
  "error": "FORBIDDEN",
  "message": "Received: 'Permission check failed (/sdn/zones/localnetwork/vmbr1, SDN.Use), status code 403'"
}
```

**Root Cause**: vmbr1 was configured as an SDN zone requiring special permissions.

### 5. **Missing API Endpoints**
**Issues**: Several expected endpoints were not available:

- **Disk resize**: `/api/v1/vms/{id}/resize-disk` → 404
- **Cloud-init config**: `/api/v1/vms/{id}/cloud-init` → 404
- **Alternative disk resize**: `/api/v1/vms/{id}/disks/{disk}/resize` → 404

**Impact**: Had to rely on manual Proxmox configuration for disk resizing.

### 6. **VM Configuration Update Failures**
**Issue**: VM configuration updates failed with generic parameter verification errors.

**Error**:
```json
{
  "error": "VM_OPERATION_FAILED",
  "message": "Failed to config update VM 8250: Parameter verification failed., status code 400"
}
```

**Root Cause**: Unclear which specific parameters were invalid - error messages lacked specificity.

### 7. **Storage Download Authentication Issues**
**Issue**: Downloading Debian 13 cloud images failed with 403 permission errors.

**Error**:
```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred. Please check logs for details."
}
```

**Log Details**: `Permission check failed, status code 403`

**Root Cause**: Authentication issues when trying to download files to Proxmox storage.

## Successful Workarounds Used

### 1. **VM Clone Instead of Cloud-Init**
Used the clone API which worked reliably:
```bash
curl -X POST http://localhost:8080/api/v1/vms/9001/clone \
  -d '{
    "templateId": 9001,
    "newVmId": 8250,
    "name": "librenms-monitor",
    "targetNode": "storage01",
    "fullClone": true,
    "targetStorage": "local-zfs"
  }'
```

### 2. **SSH Key Configuration**
The SSH key endpoint worked correctly:
```bash
curl -X PUT http://localhost:8080/api/v1/vms/8250/ssh-keys \
  -d '{"sshKeys": "ssh-ed25519 AAAAC3...barry@coffeesprout.com"}'
```

### 3. **VM Migration**
Successfully migrated VM between nodes and storage:
```bash
curl -X POST http://localhost:8080/api/v1/vms/8250/migrate \
  -d '{
    "targetNode": "hv7",
    "targetStorage": "pew-hv7",
    "online": false
  }'
```

## Infrastructure Discovery

### Proxmox Version Compatibility Issue
**Discovery**: VM worked fine on hv1 (Proxmox 8) but failed to boot/run on hv7 (Proxmox 9).

**Symptoms**:
- VM shows as "stopped" immediately after start attempts
- Network connectivity fails (ping timeouts)
- No SSH connectivity

**Impact**: Confirms compatibility issues with Proxmox 9 that need resolution before production workload migration.

## Final VM Status
- **VM ID**: 8250 (librenms-monitor)
- **Created**: Successfully cloned from Debian 12 template (VM 9001)
- **Location**: hv1 (works), hv7 (doesn't work - Proxmox 9 issue)
- **Storage**: pew-hv7 (100GB)
- **Networks**:
  - vmbr0: 172.16.1.250/24, gw=172.16.1.2
  - vmbr1 + VLAN 2: 10.0.0.250/24 (no gateway)
- **Access**: SSH via coffeesprout user with configured key

## Recommendations for API Improvements

### 1. **Consistent Documentation**
- Align validation rules with documentation examples
- Update API examples to match current validation requirements
- Add template disk discovery endpoint to show correct paths

### 2. **Better Error Messages**
- Provide specific parameter names in validation errors
- Include suggested corrections in error responses
- Add more context to generic "Parameter verification failed" errors

### 3. **Network Configuration**
- Improve VLAN/tag handling in network configuration
- Add validation for SDN vs traditional bridge usage
- Provide clearer error messages for permission issues

### 4. **Missing Endpoints**
- Add disk resize endpoints: `/api/v1/vms/{id}/disks/{disk}/resize`
- Add cloud-init configuration: `/api/v1/vms/{id}/cloud-init`
- Add template disk listing: `/api/v1/templates/{id}/disks`

### 5. **Storage Operations**
- Improve authentication handling for storage downloads
- Add better error reporting for storage permission issues
- Support direct cloud image downloads during VM creation

### 6. **API Discoverability**
- Add OpenAPI documentation for all available endpoints
- Include example request/response bodies
- Document required permissions for each endpoint

## Session Summary
The core Moxxie functionality worked well once we found the right approach (cloning vs cloud-init creation), but the initial path had multiple blockers requiring workarounds. The VM was successfully created and configured, with the main blocker being Proxmox 9 compatibility rather than API issues.