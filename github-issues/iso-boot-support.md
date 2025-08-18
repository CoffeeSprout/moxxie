# ISO Boot Support Implementation Plan

## Overview
Implement comprehensive ISO boot support in Moxxie to enable proper OKD cluster provisioning and other ISO-based installations. This feature will support multiple controller types (IDE, SCSI, SATA) with OS-aware device selection and automatic cloud-init drive relocation when needed.

## Background
Currently, Moxxie lacks the ability to attach ISO files to VMs for booting, which is required for OKD (OpenShift Kubernetes Distribution) installations using FCOS (Fedora CoreOS) images. Additionally, different operating systems have different preferences for cloud-init drive controllers (e.g., Debian prefers SCSI, while others use IDE).

## Requirements
1. Support attaching ISO files to VMs on various controller types (IDE, SCSI, SATA)
2. OS-aware automatic device selection for optimal compatibility
3. Cloud-init drive relocation when conflicts arise
4. Boot order management for ISO installations
5. Integration with cluster provisioning workflow
6. Backwards compatibility with existing cloud-init implementations
7. Comprehensive error handling and cleanup

## Implementation Plan

### Phase 1: Core Infrastructure (Priority: HIGH)

#### Step 1.1: Data Models and DTOs
Create the foundational data structures for ISO operations.

**Files to create:**
- `src/main/java/com/coffeesprout/api/dto/ISOAttachmentRequest.java`
- `src/main/java/com/coffeesprout/api/dto/MediaAttachRequest.java`
- `src/main/java/com/coffeesprout/api/dto/BootDeviceConfig.java`
- `src/main/java/com/coffeesprout/model/CDROMDeviceType.java`
- `src/main/java/com/coffeesprout/model/ControllerType.java`
- `src/main/java/com/coffeesprout/model/OSProfile.java`

```java
public record ISOAttachmentRequest(
    String deviceSlot,        // Optional - auto-detect if null
    String isoPath,          // Required - storage:iso/filename.iso
    Boolean autoDetect,      // Default true
    Boolean primaryBoot,     // Default false
    Boolean cleanupOnFailure // Default true
) {}

public record MediaAttachRequest(
    String device,           // Optional - auto-detect if null
    String content,          // ISO path or "cloudinit"
    String purpose,          // "installer", "cloudinit", "drivers"
    String osType,           // Optional - for better detection
    Boolean autoRelocate     // Auto-relocate cloud-init if needed
) {}
```

#### Step 1.2: Device Management Service
Implement intelligent device selection logic.

**File to create:** `src/main/java/com/coffeesprout/service/DeviceManagementService.java`

Key methods:
- `determineOptimalDevice(int vmId, String purpose, String osType, String ticket)`
- `analyzeVMConfig(JsonNode config)` 
- `findAvailableDevice(ControllerType controller, Set<String> usedDevices)`
- `formatDeviceConfig(String device, String content, String purpose)`
- `isDebianBased(String osType, String vmName, JsonNode metadata)`

#### Step 1.3: ISO Management Service
Core service for ISO attachment/detachment operations.

**File to create:** `src/main/java/com/coffeesprout/service/ISOManagementService.java`

Key methods:
- `attachISO(int vmId, ISOAttachmentRequest request, String ticket)`
- `detachISO(int vmId, String deviceSlot, String ticket)`
- `listAttachedMedia(int vmId, String ticket)`
- `validateISOPath(String isoPath, String ticket)`
- `relocateCloudInitDrive(int vmId, String fromDevice, String toDevice, String ticket)`

### Phase 2: OS Detection and Configuration (Priority: HIGH)

#### Step 2.1: OS Configuration Rules
Implement OS-specific configuration profiles.

**File to create:** `src/main/java/com/coffeesprout/service/OSConfigurationRules.java`

Define profiles for:
- Debian/Ubuntu (SCSI cloud-init)
- RHEL/CentOS/Fedora (IDE cloud-init)
- FCOS (IDE ISO boot, no cloud-init)
- Windows (SATA drivers)
- Generic Linux (IDE defaults)

#### Step 2.2: OS Detection Service
Implement detection logic with precedence order.

**File to create:** `src/main/java/com/coffeesprout/service/OSDetectionService.java`

Detection precedence:
1. Explicit `osType` parameter
2. VM metadata/template info
3. VM name pattern matching
4. Default to generic Linux profile

### Phase 3: Boot Order Management (Priority: HIGH)

#### Step 3.1: Boot Order Service
Manage VM boot device configuration.

**File to create:** `src/main/java/com/coffeesprout/service/BootOrderService.java`

Key methods:
- `setBootOrder(int vmId, List<BootDevice> devices, String ticket)`
- `configureISOBoot(int vmId, String isoDevice, String ticket)` - Sets disk first, ISO second
- `revertToDefaultBoot(int vmId, String ticket)`
- `getBootOrder(int vmId, String ticket)`

### Phase 4: REST API Implementation (Priority: HIGH)

#### Step 4.1: ISO Management Endpoints
Create REST endpoints for ISO operations.

**File to create:** `src/main/java/com/coffeesprout/api/ISOResource.java`

Endpoints:
- `POST /api/v1/vms/{vmId}/iso/attach` - Attach ISO with auto-detection
- `DELETE /api/v1/vms/{vmId}/iso/{deviceSlot}` - Detach ISO
- `GET /api/v1/vms/{vmId}/iso` - List attached ISOs
- `GET /api/v1/vms/{vmId}/iso/optimal-device?purpose={purpose}` - Get recommended device

#### Step 4.2: Media Management Endpoints
Unified media management including cloud-init.

**File to create:** `src/main/java/com/coffeesprout/api/MediaResource.java`

Endpoints:
- `POST /api/v1/vms/{vmId}/media/attach` - Attach any media type
- `PUT /api/v1/vms/{vmId}/media/relocate` - Relocate cloud-init drive
- `GET /api/v1/vms/{vmId}/media` - List all attached media

### Phase 5: Validation and Error Handling (Priority: HIGH)

#### Step 5.1: ISO Validator
Comprehensive validation for ISO operations.

**File to create:** `src/main/java/com/coffeesprout/validation/ISOValidator.java`

Validations:
- VM must be stopped for ISO operations
- ISO path format validation
- Storage accessibility checks
- Device conflict detection
- Boot order sanity checks

#### Step 5.2: Error Recovery Service
Handle failures gracefully.

**File to create:** `src/main/java/com/coffeesprout/service/ErrorRecoveryService.java`

Features:
- Automatic cleanup on failure (configurable)
- Transaction-like rollback for multi-step operations
- Detailed error logging with recovery suggestions

### Phase 6: Storage Integration (Priority: MEDIUM)

#### Step 6.1: Enhanced Storage Service
Extend StorageService for ISO operations.

**File to modify:** `src/main/java/com/coffeesprout/service/StorageService.java`

New methods:
- `listAvailableISOs(String storage, String ticket)`
- `downloadISO(String url, String storage, String filename, String ticket)`
- `validateISOExists(String isoPath, String ticket)`
- `getISOMetadata(String isoPath, String ticket)`

### Phase 7: OKD Integration (Priority: HIGH)

#### Step 7.1: OKD Provisioning Service
Specialized service for OKD cluster provisioning.

**File to create:** `src/main/java/com/coffeesprout/service/OKDProvisioningService.java`

Features:
- Automatic FCOS ISO attachment
- Ignition configuration application
- Bootstrap lifecycle management
- Node role detection (bootstrap, master, worker, bastion)

#### Step 7.2: Update Cluster Provisioning
Integrate ISO support into cluster provisioning.

**File to modify:** `src/main/java/com/coffeesprout/service/ClusterProvisioningService.java`

Changes:
- Detect OKD cluster type
- Attach FCOS ISOs to appropriate nodes
- Skip cloud-init for FCOS nodes
- Configure boot order for ISO installation

### Phase 8: Migration Tools (Priority: LOW)

#### Step 8.1: Cloud-init Migration Service
Tool to migrate existing VMs to optimal configuration.

**File to create:** `src/main/java/com/coffeesprout/service/CloudInitMigrationService.java`

Features:
- Detect suboptimal cloud-init placement
- Safely relocate cloud-init drives
- Batch migration for multiple VMs
- Dry-run mode for safety

### Phase 9: Testing (Priority: HIGH)

#### Step 9.1: Unit Tests
Comprehensive unit tests for all services.

**Files to create:**
- `src/test/java/com/coffeesprout/service/DeviceManagementServiceTest.java`
- `src/test/java/com/coffeesprout/service/ISOManagementServiceTest.java`
- `src/test/java/com/coffeesprout/service/OSDetectionServiceTest.java`
- `src/test/java/com/coffeesprout/service/BootOrderServiceTest.java`

#### Step 9.2: Integration Tests
Test full workflows.

**Files to create:**
- `src/test/java/com/coffeesprout/api/ISOResourceTest.java`
- `src/test/java/com/coffeesprout/api/MediaResourceTest.java`
- `src/test/java/com/coffeesprout/integration/OKDProvisioningTest.java`

### Phase 10: Documentation (Priority: MEDIUM)

#### Step 10.1: API Documentation
Update API documentation with new endpoints.

**Files to modify:**
- `API_EXAMPLES.md` - Add ISO attachment examples
- `API_SPECIFICATION.md` - Document new endpoints
- `CLAUDE.md` - Update with ISO boot information

#### Step 10.2: OKD Installation Guide
Create comprehensive OKD installation guide.

**File to create:** `docs/OKD_INSTALLATION_GUIDE.md`

## Implementation Order

1. **Week 1: Core Foundation**
   - Step 1.1: Data Models
   - Step 1.2: Device Management Service
   - Step 1.3: ISO Management Service
   - Step 5.1: ISO Validator

2. **Week 2: OS Intelligence**
   - Step 2.1: OS Configuration Rules
   - Step 2.2: OS Detection Service
   - Step 3.1: Boot Order Service

3. **Week 3: API and Integration**
   - Step 4.1: ISO Management Endpoints
   - Step 4.2: Media Management Endpoints
   - Step 7.1: OKD Provisioning Service
   - Step 7.2: Update Cluster Provisioning

4. **Week 4: Testing and Polish**
   - Step 9.1: Unit Tests
   - Step 9.2: Integration Tests
   - Step 5.2: Error Recovery Service
   - Step 10.1: API Documentation

5. **Week 5: Enhancement (Optional)**
   - Step 6.1: Enhanced Storage Service
   - Step 8.1: Cloud-init Migration Service
   - Step 10.2: OKD Installation Guide

## API Examples

### Attach ISO with Auto-detection
```bash
curl -X POST http://localhost:8080/api/v1/vms/10701/iso/attach \
  -H "Content-Type: application/json" \
  -d '{
    "isoPath": "local:iso/fedora-coreos-live.iso",
    "autoDetect": true,
    "primaryBoot": true
  }'
```

### Attach Cloud-init on Optimal Device
```bash
curl -X POST http://localhost:8080/api/v1/vms/8201/media/attach \
  -H "Content-Type: application/json" \
  -d '{
    "content": "cloudinit",
    "purpose": "cloudinit",
    "osType": "debian12",
    "autoRelocate": true
  }'
```

### OKD Node with ISO
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d '{
    "clusterType": "OKD",
    "nodes": [{
      "name": "okd-master-1",
      "template": "fcos-template",
      "bootISO": {
        "isoPath": "nfs-iso:iso/fedora-coreos-39-live.iso",
        "primaryBoot": true
      }
    }]
  }'
```

## Success Criteria

1. ✅ ISO files can be attached to VMs on IDE, SCSI, or SATA controllers
2. ✅ OS-aware automatic device selection works correctly
3. ✅ Cloud-init drives are automatically relocated when needed
4. ✅ Boot order is correctly configured for ISO installations
5. ✅ OKD clusters can be provisioned with FCOS ISOs
6. ✅ Existing cloud-init functionality remains compatible
7. ✅ Comprehensive error handling with cleanup options
8. ✅ All unit and integration tests pass
9. ✅ API documentation is complete and accurate

## Optional Enhancements (Future)

### Phase 11: Advanced Features
1. **ISO Library Management**
   - Pre-cached ISO repository
   - Automatic ISO download from vendor URLs
   - ISO checksum verification
   - ISO metadata extraction

2. **Smart Boot Management**
   - Detect installation completion
   - Automatic ISO ejection after install
   - Boot device priority templates
   - PXE boot integration

3. **Multi-Stage Installations**
   - Support for driver ISOs
   - Chained ISO installations
   - Post-install automation hooks
   - Custom ignition/kickstart injection

4. **Enhanced OKD Support**
   - Automatic FCOS version detection
   - Ignition validation
   - Bootstrap cleanup automation
   - Cluster health monitoring

5. **UI Integration**
   - ISO browser/selector
   - Drag-and-drop ISO attachment
   - Visual boot order configuration
   - Installation progress tracking

## Risk Mitigation

1. **Backwards Compatibility**: Maintain existing IDE2 cloud-init behavior by default
2. **Data Loss**: Always validate VM is stopped before device changes
3. **Boot Failures**: Provide clear boot order reset mechanisms
4. **Storage Issues**: Validate ISO accessibility before attachment
5. **Cleanup Failures**: Implement robust error recovery with manual override options

## Notes

- FCOS images are used for OKD, not RHCOS
- Proxmox will skip empty disks and try ISO automatically when boot order is disk-first
- Different Linux distributions have different cloud-init device preferences
- NFS storage is supported for ISO hosting
- Safe mode restrictions should apply to ISO operations
- Audit logging is required for all ISO attach/detach operations