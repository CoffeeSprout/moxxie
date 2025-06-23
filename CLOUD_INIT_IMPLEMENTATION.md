# Cloud-Init VM Implementation Summary

## Overview
This document summarizes the implementation of cloud-init VM creation functionality in Moxxie, addressing GitHub issue #62.

## Problem Statement
We needed to create VMs from cloud images (Debian, Ubuntu, Talos) using the Proxmox API, similar to how it's done with Ansible's `proxmox_disk` module.

## What We Tried

### Attempt 1: Direct import-from during VM creation
- Tried to use `import-from` parameter directly in the VM creation request
- Failed with "unable to parse directory volume name" errors
- Proxmox couldn't recognize the image path format

### Attempt 2: Download cloud images via API
- Attempted to download cloud images to storage using the download-url API
- Failed due to permission issues (403 errors)
- Even after granting permissions, the content-type detection was problematic

### Attempt 3: Manual image placement
- Manually downloaded cloud images to the storage filesystem
- Images weren't recognized by Proxmox storage API
- The import-from still failed with path parsing errors

## Final Solution: Two Approaches

### Direct Import (Works with Templates)
For existing Proxmox templates, the direct import-from approach works:
- Include `import-from` parameter in the disk configuration during VM creation
- Template must be on the same node as the target VM
- Works with properly registered template disks (e.g., `util-iso:9000/base-9000-disk-0.qcow2`)

### Two-Step Approach (For Flexibility)
We also implemented an Ansible-style two-step approach:
1. **Create VM without main disk** - Only attach cloud-init drive (ide2)
2. **Import disk separately** - Use the `updateDisk` API to import and attach the disk
3. **Configure cloud-init** - Set user, password, SSH keys, and network configuration

The two-step approach provides more control and error handling but isn't strictly necessary for template imports.

### Implementation Details

```java
// Step 1: Create VM without main disk
CreateVMRequest clientRequest = new CreateVMRequest();
clientRequest.setIde2(targetStorage + ":cloudinit");
clientRequest.setBoot("order=ide2");
// ... other config ...

// Step 2: Import disk using updateDisk
vmService.importDisk(node, vmId, diskConfig, ticket);

// Step 3: Update boot order and cloud-init settings
vmService.updateVMConfig(node, vmId, bootConfig, ticket);
```

### Key Requirements
- VM must be created on the same node as the source image/template
- Source must be a recognized Proxmox storage content item (e.g., template disk)
- Cloud images need to be properly imported into Proxmox first

### API Endpoint
```bash
POST /api/v1/vms/cloud-init
```

## Current Limitations
1. Cannot directly import from filesystem paths
2. Requires existing templates or properly registered storage content
3. Import operations can be slow (20+ seconds)
4. SSH keys must be properly formatted or omitted

## Next Steps
For using custom cloud images (Debian 12, Talos), you need to:
1. Import the images as templates using `qm` commands
2. Use the template's disk path with the cloud-init endpoint