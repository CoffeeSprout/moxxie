# Phase 6: Infrastructure Provisioning - PXE Boot Support

## Overview
Add PXE boot support to enable bare metal provisioning. MikroTik/FS switches handle DHCP/TFTP while Moxxie serves HTTP-based iPXE menus and boot resources.

## Architecture

```
┌─────────────────────┐
│   PXE Client        │
└──────────┬──────────┘
           │ 1. DHCP Request
┌──────────▼──────────┐
│  MikroTik Switch    │
│  - DHCP Server      │
│  - TFTP Server      │
└──────────┬──────────┘
           │ 2. iPXE chainload
┌──────────▼──────────┐
│   Moxxie Instance   │
│  - HTTP Boot Menu   │
│  - Boot Resources   │
│  - Dynamic Configs  │
└─────────────────────┘
```

## Key Features

### 1. iPXE Menu Service
- Dynamic menu generation based on MAC/UUID
- Support for multiple boot profiles
- BIOS and UEFI compatibility
- Template-based configuration

### 2. Boot Profile Management
- Talos Linux (control plane, worker)
- Ubuntu Server
- Rescue systems
- Custom profiles

### 3. MAC Address Mapping
- Automatic profile assignment
- Parameter injection per node
- Integration with Proxmox VM discovery

### 4. Network Configuration Docs
- Auto-generate MikroTik configs
- FS switch configurations
- Troubleshooting guides

## Implementation Details

### Endpoints

```
# iPXE Boot Menus
GET /boot/menu.ipxe?mac={mac}&uuid={uuid}
GET /boot/{profile}.ipxe

# Static Resources  
GET /boot/assets/{profile}/{file}

# Configuration API
GET /api/v1/boot/profiles
POST /api/v1/boot/profiles
GET /api/v1/boot/assignments
POST /api/v1/boot/assignments

# Network Config Docs
GET /api/v1/boot/network-config?vendor=mikrotik
```

### Database Schema

```sql
-- Boot profiles
CREATE TABLE boot_profiles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) UNIQUE,
    profile_type VARCHAR(50),
    kernel_path VARCHAR(500),
    initrd_path VARCHAR(500),
    boot_params TEXT,
    config_template TEXT
);

-- MAC to node assignments
CREATE TABLE node_boot_assignments (
    mac_address VARCHAR(17) PRIMARY KEY,
    node_name VARCHAR(255),
    boot_profile_id UUID,
    boot_params_override JSONB,
    last_boot_at TIMESTAMP
);
```

### Security Controls

1. **Network Isolation**
   - IP allowlist for management networks
   - Rate limiting per client IP

2. **Authentication**
   - Public boot menus (no auth)
   - Protected config files (auth required)

3. **Audit Logging**
   - Track all boot requests
   - Monitor config access

## Example Configurations

### Talos Control Plane iPXE Menu
```
#!ipxe
echo Booting Talos Control Plane: ${net0/mac}
kernel http://moxxie/boot/assets/talos/vmlinuz
initrd http://moxxie/boot/assets/talos/initramfs.xz
imgargs vmlinuz talos.platform=metal 
imgargs vmlinuz talos.config=http://moxxie/boot/configs/talos-cp/${net0/mac}
boot
```

### MikroTik DHCP Configuration
```
/ip dhcp-server network
add address=10.0.0.0/24 gateway=10.0.0.1 \
    boot-file-name="undionly.kpxe" \
    next-server=10.0.0.10
```

## Testing Strategy

1. **Unit Tests**
   - Template rendering
   - MAC address normalization
   - Profile validation

2. **Integration Tests**
   - iPXE client simulation
   - Boot sequence verification
   - Network config generation

3. **Performance Tests**
   - Concurrent boot requests
   - Large file serving
   - Template caching

## Deployment Considerations

1. **Storage Requirements**
   - ~500MB per OS profile
   - Kernel and initrd files
   - Config templates

2. **Network Bandwidth**
   - Support 10+ concurrent boots
   - Efficient file serving
   - Caching strategies

3. **High Availability**
   - Static assets on shared storage
   - Database replication
   - Load balancer friendly

## Use Cases

### Talos Cluster Provisioning
1. Configure boot profiles for control plane and workers
2. Assign MACs to nodes with role-specific parameters
3. Boot nodes via PXE
4. Talos automatically forms cluster

### Bare Metal Recovery
1. Create rescue boot profile
2. Boot failed node via PXE
3. Access rescue environment
4. Perform maintenance

### OS Deployment
1. Upload OS installation media
2. Create boot profile with preseed/kickstart
3. PXE boot target machines
4. Automated OS installation

## Success Metrics

- Reduce bare metal provisioning from hours to minutes
- Support 50+ concurrent PXE boots
- Zero-touch Talos cluster deployment
- 99% boot success rate

## Future Enhancements

1. **Secure Boot Support**
   - Signed boot loaders
   - Certificate management

2. **Cloud-Init Integration**
   - Dynamic user-data generation
   - Per-node customization

3. **Boot Analytics**
   - Success/failure tracking
   - Performance metrics
   - Predictive maintenance