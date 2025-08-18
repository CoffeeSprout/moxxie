# UEFI/OVMF Support in Moxxie

## Overview
Moxxie now provides comprehensive support for UEFI (Unified Extensible Firmware Interface) and OVMF (Open Virtual Machine Firmware) to enable deployment of modern operating systems like SCOS (CentOS Stream CoreOS) used by OKD 4.19+.

## Key Features
- ✅ UEFI/OVMF firmware configuration
- ✅ EFI disk management with auto-creation
- ✅ Machine type selection (PC, Q35)
- ✅ Secure boot support (optional)
- ✅ Backward compatibility with SeaBIOS
- ✅ VM configuration endpoints (GET/PUT)
- ✅ Comprehensive validation

## Why UEFI is Required for Modern OS

Modern operating systems like SCOS (used by OKD 4.19) and FCOS require UEFI boot:
- **PXE Boot**: UEFI systems use GRUB EFI instead of PXELINUX
- **Network Boot**: HTTP module support for network installations
- **Security**: Support for secure boot and TPM
- **Compatibility**: Future-proofing for newer OS releases

## Firmware Configuration

### Data Model
```java
public record FirmwareConfig(
    FirmwareType type,        // SEABIOS or UEFI
    MachineType machine,      // PC or Q35  
    EFIDiskConfig efidisk,    // Required for UEFI
    Boolean secureboot        // Optional secure boot
) {}

public record EFIDiskConfig(
    String storage,           // Storage pool (e.g., "local-zfs")
    EFIType efitype,         // SMALL (2m) or LARGE (4m)
    Boolean preEnrolledKeys  // For secure boot
) {}
```

### Supported Configurations

| Firmware | Machine | EFI Disk | Use Case |
|----------|---------|----------|----------|
| SeaBIOS | PC | None | Legacy OS, Windows |
| UEFI | Q35 | Required | Modern Linux, OKD, FCOS |

## API Usage Examples

### 1. Create UEFI VM for OKD Bootstrap

```bash
curl -X POST http://localhost:8080/api/v1/vms/cloudinit \
  -H "Content-Type: application/json" \
  -d '{
    "name": "okd-bootstrap",
    "node": "storage01",
    "cores": 4,
    "memoryMB": 16384,
    "imageSource": "local-zfs:9002/base-9002-disk-0.raw",
    "targetStorage": "local-zfs",
    "diskSizeGB": 120,
    "cloudInitUser": "admin",
    "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... admin@example.com",
    "searchDomain": "cluster.local",
    "nameservers": "8.8.8.8,8.8.4.4",
    "cpuType": "host",
    "description": "OKD Bootstrap Node",
    "tags": "okd,bootstrap",
    "firmware": {
      "type": "UEFI",
      "machine": "Q35",
      "efidisk": {
        "storage": "local-zfs",
        "efitype": "LARGE",
        "preEnrolledKeys": false
      },
      "secureboot": false
    },
    "scsihw": "virtio-scsi-single",
    "serial0": "socket",
    "vgaType": "serial0"
  }'
```

### 2. Create Traditional SeaBIOS VM

```bash
curl -X POST http://localhost:8080/api/v1/vms/cloudinit \
  -H "Content-Type: application/json" \
  -d '{
    "name": "debian-server",
    "node": "hv7",
    "cores": 2,
    "memoryMB": 4096,
    "imageSource": "local-zfs:9001/base-9001-disk-0.raw",
    "targetStorage": "local-zfs",
    "diskSizeGB": 50,
    "cloudInitUser": "debian",
    "description": "Debian Server",
    "firmware": {
      "type": "SEABIOS",
      "machine": "PC",
      "secureboot": false
    },
    "scsihw": "virtio-scsi-pci",
    "vgaType": "std"
  }'
```

### 3. Get VM Configuration

```bash
curl -X GET http://localhost:8080/api/v1/vms/10710/config

# Response includes firmware settings:
{
  "vmid": 10710,
  "name": "okd-bootstrap",
  "machine": "q35",
  "bios": "ovmf",
  "efidisk0": "local-zfs:1,efitype=4m,pre-enrolled-keys=0",
  "cores": 4,
  "memory": 16384,
  "scsihw": "virtio-scsi-single"
}
```

### 4. Update VM Configuration

```bash
curl -X PUT http://localhost:8080/api/v1/vms/200/config \
  -H "Content-Type: application/json" \
  -d '{
    "cores": "4",
    "memory": "8192",
    "description": "Updated server configuration"
  }'
```

### 5. Update Firmware Configuration

```bash
curl -X POST http://localhost:8080/api/v1/vms/200/firmware \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UEFI",
    "machine": "Q35",
    "efidisk": {
      "storage": "local-zfs",
      "efitype": "LARGE",
      "preEnrolledKeys": false
    },
    "secureboot": false
  }'
```

## Configuration Options

### Firmware Types
- **SEABIOS**: Traditional BIOS (legacy)
- **UEFI**: Modern UEFI firmware (OVMF)

### Machine Types
- **PC**: i440fx chipset (legacy, compatible)
- **Q35**: Modern chipset (required for UEFI)

### EFI Disk Types
- **SMALL (2m)**: 2MB EFI disk
- **LARGE (4m)**: 4MB EFI disk (recommended)

### Hardware Configuration
- **SCSI Hardware**: `virtio-scsi-pci` (default), `virtio-scsi-single`
- **Serial Console**: `socket` for text console
- **VGA**: `serial0` for text, `std` for graphics

## OKD Cluster Deployment Example

For a complete OKD 4.19 cluster using PXE boot with UEFI:

```bash
# Bootstrap Node
curl -X POST http://localhost:8080/api/v1/vms/cloudinit \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 10710,
    "name": "okd-bootstrap",
    "node": "storage01",
    "firmware": {
      "type": "UEFI",
      "machine": "Q35",
      "efidisk": {
        "storage": "local-zfs",
        "efitype": "LARGE",
        "preEnrolledKeys": false
      }
    },
    "bootOrder": "order=scsi0;net0",
    "scsihw": "virtio-scsi-single",
    "serial0": "socket",
    "vgaType": "serial0"
  }'

# Master Nodes (repeat for each)
curl -X POST http://localhost:8080/api/v1/vms/cloudinit \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 10711,
    "name": "okd-master-1",
    "node": "hv5",
    "firmware": {
      "type": "UEFI",
      "machine": "Q35",
      "efidisk": {
        "storage": "local-zfs",
        "efitype": "LARGE",
        "preEnrolledKeys": false
      }
    }
  }'
```

## Validation Rules

The API enforces these validation rules:

1. **UEFI requires Q35**: UEFI firmware must use Q35 machine type
2. **EFI disk for UEFI**: UEFI VMs must have EFI disk configuration
3. **No EFI disk for SeaBIOS**: SeaBIOS VMs cannot have EFI disk
4. **Secure boot requires UEFI**: Secure boot only available with UEFI

### Error Examples

```bash
# Invalid: UEFI without EFI disk
{
  "error": "EFI disk configuration is required for UEFI firmware"
}

# Invalid: UEFI with PC machine type  
{
  "error": "UEFI firmware requires q35 machine type, not pc"
}

# Invalid: Secure boot with SeaBIOS
{
  "error": "Secure boot is only available with UEFI firmware"
}
```

## Backward Compatibility

- Existing VMs continue to work unchanged
- Default firmware is SeaBIOS for compatibility
- Omitting firmware configuration uses SeaBIOS + PC
- All existing APIs remain functional

## Best Practices

### For Modern OS (OKD, FCOS, Talos)
```json
{
  "firmware": {
    "type": "UEFI",
    "machine": "Q35",
    "efidisk": {
      "storage": "local-zfs",
      "efitype": "LARGE",
      "preEnrolledKeys": false
    }
  },
  "scsihw": "virtio-scsi-single",
  "serial0": "socket",
  "vgaType": "serial0"
}
```

### For Legacy OS (Windows, older Linux)
```json
{
  "firmware": {
    "type": "SEABIOS", 
    "machine": "PC"
  },
  "scsihw": "virtio-scsi-pci",
  "vgaType": "std"
}
```

### For Secure Environments
```json
{
  "firmware": {
    "type": "UEFI",
    "machine": "Q35", 
    "efidisk": {
      "storage": "local-zfs",
      "efitype": "SMALL",
      "preEnrolledKeys": true
    },
    "secureboot": true
  }
}
```

## Troubleshooting

### Common Issues

**Problem**: VM fails to boot with UEFI
**Solution**: Ensure template/image supports UEFI boot

**Problem**: PXE boot doesn't work
**Solution**: Verify boot order: `"order=scsi0;net0"`

**Problem**: OKD installation fails
**Solution**: Use UEFI with Q35, serial console configuration

### Boot Order for PXE
```json
{
  "bootOrder": "order=scsi0;net0"
}
```
This boots from disk first, falls back to PXE for installation.

## Migration from SeaBIOS to UEFI

⚠️ **Warning**: Converting existing VMs from SeaBIOS to UEFI requires:
1. VM must be stopped
2. OS must support UEFI boot
3. Boot loader reconfiguration may be needed

```bash
# Update firmware configuration
curl -X POST http://localhost:8080/api/v1/vms/{vmId}/firmware \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UEFI",
    "machine": "Q35",
    "efidisk": {
      "storage": "local-zfs",
      "efitype": "LARGE",
      "preEnrolledKeys": false
    }
  }'
```

## Technical Implementation Details

### EFI Disk Creation
- EFI disks are automatically created during VM creation
- Size is always 1MB in Proxmox (parameter is fixed)
- Type (2m/4m) refers to NVRAM template size
- Storage location follows main VM disk storage

### Proxmox Integration
```
machine=q35
bios=ovmf
efidisk0=local-zfs:1,efitype=4m,pre-enrolled-keys=0
```

### VM Builder Pattern
The implementation uses builder patterns for clean API:
```java
CreateVMRequestBuilder.builder()
    .vmid(vmId)
    .machine("q35")
    .bios("ovmf")
    .efidisk0("local-zfs:1,efitype=4m,pre-enrolled-keys=0")
    .build();
```

This comprehensive UEFI/OVMF support enables Moxxie to deploy modern Kubernetes distributions and operating systems that require UEFI boot capabilities.