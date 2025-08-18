# PXE Boot Infrastructure Support

## Overview
Implement PXE (Preboot Execution Environment) boot support in Moxxie to enable network-based OS installations for VMs. This feature focuses on VM boot infrastructure provisioning - creating VMs configured for PXE boot, managing boot files, and optionally providing DHCP/TFTP configuration. Application-level services (HAProxy, nginx configs) are out of scope.

## Background
Our recent OKD 4.19 cluster deployment revealed that PXE boot is superior to ISO-based installation for:
- **Reliability**: No media attachment/ejection issues
- **Scalability**: Single HTTP/TFTP server serves unlimited nodes
- **Intelligence**: MAC-aware boot configurations for role-specific installations
- **Automation**: No manual ISO switching between node types
- **Performance**: Faster than mounting/unmounting ISOs

## Success Story
We successfully deployed a 6-node OKD 4.19 cluster using:
- **dnsmasq**: DHCP, DNS, and TFTP services
- **nginx**: HTTP server for boot files and ignition configs
- **GRUB EFI**: MAC-aware boot menus showing role-specific options
- **Boot Order**: `scsi0;net0` prevents reinstall loops
- **Zero Manual Intervention**: Each VM type automatically gets correct configuration

## Requirements

### Core PXE Infrastructure
1. **VM PXE Boot Configuration**: Configure VMs with proper boot order and PXE settings
2. **Boot File Management**: Download and organize kernel/initramfs/rootfs files
3. **MAC Address Management**: Track and reserve MAC addresses for PXE clients
4. **Boot Order API**: Set disk-first, network-fallback configuration via API
5. **DHCP/TFTP Config Generation** (Optional): Generate configs for external DHCP/TFTP servers

### Supported Platforms
1. **OKD/OpenShift**: SCOS (CentOS Stream CoreOS) with ignition configs
2. **Talos**: Talos Linux for Kubernetes clusters
3. **Generic Linux**: Ubuntu, Debian, CentOS with kickstart/preseed
4. **Container-Optimized**: Flatcar, RancherOS, etc.

### Integration Features
1. **Cluster Provisioning Integration**: PXE as first-class cluster deployment option
2. **Template Management**: Pre-configured boot templates for common platforms
3. **Network Topology Awareness**: Multi-VLAN and isolated network support
4. **Load Balancer Integration**: Combined PXE + HAProxy deployment

## Implementation Plan

### Phase 1: Core PXE Infrastructure (Priority: HIGH)

#### Step 1.1: PXE Server Management Service
**File to create:** `src/main/java/com/coffeesprout/service/PXEServerService.java`

Key methods:
- `deployPXEServer(int vmId, PXEServerConfig config, String ticket)`
- `configureDnsmasq(PXEServerConfig config, List<PXEClient> clients)`
- `configureHTTPServer(PXEServerConfig config, List<BootFile> files)`
- `generateDHCPStaticAssignments(List<PXEClient> clients)`
- `restartPXEServices(int vmId, String ticket)`

#### Step 1.2: Boot Configuration Generator
**File to create:** `src/main/java/com/coffeesprout/service/BootConfigGenerator.java`

Key methods:
- `generateGRUBConfig(List<PXEClient> clients, PlatformType platform)`
- `generateMACBasedMenu(PXEClient client, BootTemplate template)`
- `createRoleSpecificBootEntry(NodeRole role, PlatformConfig platform)`
- `validateBootConfiguration(String grubConfig)`

#### Step 1.3: Network Boot File Manager
**File to create:** `src/main/java/com/coffeesprout/service/NetworkBootFileManager.java`

Key methods:
- `downloadPlatformFiles(PlatformType platform, String version, String ticket)`
- `organizePXEFiles(PlatformType platform, Map<String, byte[]> files)`
- `generateIgnitionConfigs(ClusterConfig cluster, String ticket)`
- `serveBootFiles(String platform, String version, HttpServletRequest request)`

### Phase 2: Platform Support (Priority: HIGH)

#### Step 2.1: OKD/SCOS Support
**File to create:** `src/main/java/com/coffeesprout/service/platform/OKDPXESupport.java`

Features:
- SCOS kernel/initramfs/rootfs download from Red Hat
- Ignition config generation (bootstrap, master, worker)
- MAC-based role detection
- Cluster formation monitoring integration

#### Step 2.2: Talos Support
**File to create:** `src/main/java/com/coffeesprout/service/platform/TalosPXESupport.java`

Features:
- Talos Linux ISO extraction for PXE files
- Machine config generation for different node types
- Talos API integration for cluster bootstrapping
- Encryption and security configuration

#### Step 2.3: Generic Linux Support
**File to create:** `src/main/java/com/coffeesprout/service/platform/GenericLinuxPXESupport.java`

Features:
- Ubuntu/Debian preseed configuration
- CentOS/RHEL kickstart configuration
- Custom kernel parameter injection
- Post-install script automation

### Phase 3: Data Models and Configuration (Priority: HIGH)

#### Step 3.1: PXE Configuration Models
**Files to create:**
- `src/main/java/com/coffeesprout/model/pxe/PXEServerConfig.java`
- `src/main/java/com/coffeesprout/model/pxe/PXEClient.java`
- `src/main/java/com/coffeesprout/model/pxe/BootTemplate.java`
- `src/main/java/com/coffeesprout/model/pxe/PlatformConfig.java`

```java
public record PXEServerConfig(
    String networkInterface,     // eth1 for internal network
    String dhcpRange,            // 10.1.107.100-150
    String serverIP,             // 10.1.107.1
    List<String> dnsServers,     // [1.1.1.1, 8.8.8.8]
    String domain,               // cluster.domain.com
    String tftpRoot,             // /var/lib/tftpboot
    String httpRoot,             // /var/www/pxe
    int httpPort                 // 8080
) {}

public record PXEClient(
    String macAddress,           // bc:24:11:b8:90:3b
    String hostname,             // bootstrap
    String ipAddress,            // 10.1.107.10
    NodeRole role,               // BOOTSTRAP, MASTER, WORKER
    PlatformType platform,       // OKD, TALOS, UBUNTU
    String bootTemplate,         // template name
    Map<String, String> metadata // role-specific config
) {}
```

### Phase 4: REST API Implementation (Priority: HIGH)

#### Step 4.1: PXE Management Endpoints
**File to create:** `src/main/java/com/coffeesprout/api/PXEResource.java`

Endpoints:
- `POST /api/v1/pxe/servers` - Deploy PXE server VM
- `PUT /api/v1/pxe/servers/{serverId}/clients` - Add/update PXE clients
- `GET /api/v1/pxe/servers/{serverId}/config` - Get current configuration
- `POST /api/v1/pxe/servers/{serverId}/restart` - Restart PXE services
- `GET /api/v1/pxe/platforms/{platform}/files` - List available boot files

#### Step 4.2: Cluster PXE Integration
**File to modify:** `src/main/java/com/coffeesprout/api/ClusterResource.java`

New features:
- Support `bootMethod: "PXE"` in cluster provisioning requests
- Automatic PXE server deployment for clusters
- PXE client configuration generation from cluster spec
- Integrated cluster + PXE deployment workflow

### Phase 5: Advanced Features (Priority: MEDIUM)

#### Step 5.1: Multi-Platform Boot Server
**File to create:** `src/main/java/com/coffeesprout/service/MultiPlatformPXEService.java`

Features:
- Single PXE server supporting multiple platforms
- Platform detection based on MAC address ranges
- Automatic file organization and serving
- Resource cleanup and optimization

#### Step 5.2: PXE Template Management
**File to create:** `src/main/java/com/coffeesprout/service/PXETemplateService.java`

Features:
- Pre-built boot templates for common platforms
- Custom template creation and validation
- Template versioning and rollback
- Community template sharing (future)

### Phase 6: Monitoring and Diagnostics (Priority: MEDIUM)

#### Step 6.1: PXE Monitoring Service
**File to create:** `src/main/java/com/coffeesprout/service/PXEMonitoringService.java`

Features:
- DHCP lease monitoring
- TFTP transfer tracking
- HTTP boot file access logging
- Boot failure detection and alerting

#### Step 6.2: PXE Diagnostics
**File to create:** `src/main/java/com/coffeesprout/service/PXEDiagnosticsService.java`

Features:
- Network connectivity testing
- PXE service health checks
- Boot configuration validation
- Client boot progress tracking

## API Examples

### Deploy OKD Cluster with PXE
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d '{
    "name": "okd-prod",
    "type": "OKD",
    "bootMethod": "PXE",
    "pxeServer": {
      "network": "10.1.107.0/24",
      "serverVM": {
        "vmId": 500,
        "interfaces": ["public", "internal"]
      }
    },
    "nodeGroups": [
      {
        "name": "bootstrap",
        "count": 1,
        "role": "BOOTSTRAP",
        "placement": {"targetNodes": ["storage01"]}
      },
      {
        "name": "masters",
        "count": 3,
        "role": "MASTER",
        "placement": {"antiAffinity": "SOFT"}
      }
    ]
  }'
```

### Add PXE Clients to Existing Server
```bash
curl -X PUT http://localhost:8080/api/v1/pxe/servers/500/clients \
  -H "Content-Type: application/json" \
  -d '{
    "clients": [
      {
        "macAddress": "bc:24:11:b8:90:3b",
        "hostname": "bootstrap",
        "ipAddress": "10.1.107.10",
        "role": "BOOTSTRAP",
        "platform": "OKD",
        "bootTemplate": "scos-bootstrap"
      }
    ]
  }'
```

### Deploy Talos Cluster with PXE
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d '{
    "name": "talos-k8s",
    "type": "TALOS",
    "bootMethod": "PXE",
    "nodeGroups": [
      {
        "name": "controlplane",
        "count": 3,
        "role": "CONTROL_PLANE"
      },
      {
        "name": "workers", 
        "count": 3,
        "role": "WORKER"
      }
    ]
  }'
```

## Technical Implementation Details

### Boot Order Strategy
- **Primary**: `scsi0` (disk) - boots installed OS after installation
- **Fallback**: `net0` (network) - PXE boots for initial installation
- **Result**: No reinstall loops, automatic transition to installed OS

### GRUB Configuration Pattern
```grub
# MAC-aware menu selection
if [ "${net_default_mac}" = "bc:24:11:b8:90:3b" ]; then
    # Bootstrap-specific boot entry
    menuentry "OKD Bootstrap Install" {
        linux /okd/kernel ... ignition_url=http://pxe-server/bootstrap.ign
        initrd /okd/initramfs
    }
fi
```

### Network Architecture
```
┌─────────────────┐    ┌──────────────┐    ┌──────────────┐
│   PXE Server    │    │  Bootstrap   │    │   Masters    │
│  (Load Balancer)│    │              │    │              │
│                 │    │              │    │              │
│ ┌─────────────┐ │    │              │    │              │
│ │  dnsmasq    │ │───▶│  PXE Boot    │    │  PXE Boot    │
│ │  nginx      │ │    │              │    │              │
│ │  HAProxy    │ │    │              │    │              │
│ └─────────────┘ │    │              │    │              │
└─────────────────┘    └──────────────┘    └──────────────┘
         │                       │                  │
         └───────────────────────┼──────────────────┘
                                 │
                    ┌──────────────┐
                    │   Workers    │
                    │              │
                    │  PXE Boot    │
                    │              │
                    └──────────────┘
```

## Success Criteria

1. ✅ PXE servers can be automatically deployed and configured
2. ✅ MAC-aware boot configurations work reliably
3. ✅ OKD clusters deploy successfully via PXE
4. ✅ Talos clusters deploy successfully via PXE
5. ✅ Boot order prevents reinstall loops
6. ✅ Multiple platforms supported on single PXE server
7. ✅ Integration with existing cluster provisioning workflow
8. ✅ Comprehensive monitoring and diagnostics available
9. ✅ All unit and integration tests pass
10. ✅ Documentation and examples complete

## Benefits Over ISO Boot

1. **Reliability**: No ISO mounting/unmounting issues
2. **Scalability**: Single server supports unlimited clients
3. **Intelligence**: Role-aware automatic configuration
4. **Performance**: Faster than physical media operations
5. **Automation**: Zero manual intervention required
6. **Flexibility**: Easy platform switching and updates
7. **Monitoring**: Full visibility into boot process
8. **Recovery**: Easy rollback and reconfiguration

## Future Enhancements

1. **iPXE Support**: HTTP-only booting without TFTP
2. **Secure Boot**: UEFI Secure Boot with signed bootloaders
3. **Multi-Arch**: ARM64 support for edge deployments
4. **Cloud Integration**: PXE server deployment in cloud environments
5. **UI Integration**: Visual PXE configuration and monitoring
6. **Template Marketplace**: Community-shared boot templates
7. **Auto-Discovery**: Automatic client detection and classification
8. **High Availability**: Redundant PXE server deployments

## Risk Mitigation

1. **Network Dependencies**: Validate network connectivity before deployment
2. **DHCP Conflicts**: Check for existing DHCP servers in target networks
3. **File Corruption**: Checksum validation for all boot files
4. **Boot Loops**: Proper boot order configuration and validation
5. **Storage Space**: Monitor disk usage for boot files and logs
6. **Security**: Network isolation and access controls for PXE traffic

## Notes

- PXE boot is particularly effective for homogeneous cluster deployments
- UEFI systems require GRUB EFI bootloader instead of PXELINUX
- Network isolation is critical for PXE security
- Boot file caching improves performance for large deployments
- Integration with existing network infrastructure requires careful planning