# Boot Method Selection API for VM Creation

## Overview
Enhance the VM creation API to support explicit boot method selection, allowing users to specify how VMs should boot (PXE, ISO, cloud-init, or template-based). This gives users fine-grained control over VM provisioning methods based on their specific requirements.

## Background
Currently, Moxxie assumes cloud-init for VM provisioning. However, different scenarios require different boot methods:
- **PXE**: Network boot for automated OS installation (OKD, Talos)
- **ISO**: Media-based installation for offline or specialized deployments
- **Cloud-init**: Cloud-native provisioning with user-data/meta-data
- **Template**: Clone from existing template for fastest deployment

## Requirements

### API Enhancements
1. Add `bootMethod` parameter to VM creation API
2. Support boot order configuration (e.g., `["scsi0", "net0"]`)
3. Validate boot method compatibility with other parameters
4. Auto-detect optimal boot method if not specified
5. Return boot configuration in VM info responses

### Boot Methods
1. **PXE**: Configure for network boot with proper NIC settings
2. **ISO**: Attach ISO with appropriate controller type
3. **CLOUD_INIT**: Current default behavior
4. **TEMPLATE**: Clone from template without cloud-init
5. **HYBRID**: Combination methods for complex scenarios

## Implementation

### Data Models
```java
public enum BootMethod {
    PXE,        // Network boot via DHCP/TFTP
    ISO,        // ISO file attachment
    CLOUD_INIT, // Cloud-init with user-data
    TEMPLATE,   // Template clone without modification
    HYBRID      // Custom combination
}

public record BootConfiguration(
    BootMethod method,
    List<String> bootOrder,    // ["scsi0", "net0"]
    String isoPath,            // For ISO method
    String pxeServer,          // For PXE method
    Map<String, Object> options
) {}
```

### API Examples

#### Create VM with PXE Boot
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 10710,
    "name": "okd-bootstrap",
    "bootMethod": "PXE",
    "bootOrder": ["scsi0", "net0"],
    "networks": [{
      "model": "virtio",
      "bridge": "vmbr0",
      "vlanTag": 107
    }],
    "uefi": true
  }'
```

#### Create VM with ISO Boot
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 200,
    "name": "windows-server",
    "bootMethod": "ISO",
    "isoPath": "local:iso/windows-2022.iso",
    "bootOrder": ["ide2", "scsi0"],
    "uefi": true
  }'
```

#### Create VM with Template (No Cloud-init)
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 300,
    "name": "app-server",
    "bootMethod": "TEMPLATE",
    "templateId": 9001,
    "skipCloudInit": true
  }'
```

## Benefits
1. **Flexibility**: Support diverse deployment scenarios
2. **Clarity**: Explicit boot method makes automation clearer
3. **Compatibility**: Better OS and platform support
4. **Performance**: Choose optimal method for use case
5. **Reliability**: Proper boot order prevents issues

## Success Criteria
1. ✅ All boot methods work correctly
2. ✅ Boot order configuration is applied properly
3. ✅ Backwards compatibility maintained
4. ✅ Clear error messages for invalid configurations
5. ✅ Documentation and examples provided

## Related Issues
- #78: ISO Boot Support
- #81: UEFI/OVMF Support
- PXE Boot Infrastructure Support (local)