# Talos Linux Kubernetes Cluster Support

## Overview
Implement comprehensive support for deploying and managing Talos Linux Kubernetes clusters through Moxxie. Talos is a security-focused, API-driven, and immutable Linux distribution designed specifically for Kubernetes, making it an excellent choice for production Kubernetes deployments.

## Background
Talos Linux offers several advantages for Kubernetes clusters:
- **Security-First**: Minimal attack surface, no SSH, no shell access
- **API-Driven**: All configuration via machine configuration files
- **Immutable**: Read-only root filesystem with atomic updates
- **Cloud-Native**: Purpose-built for Kubernetes workloads
- **Simple**: Minimal components, easy to understand and debug
- **PXE-Ready**: Excellent network boot support for automated deployment

Based on our successful OKD PXE implementation, Talos would benefit from similar network boot capabilities for seamless cluster provisioning.

## Requirements

### Core Talos Support
1. **Machine Configuration Generation**: Talos machine configs for different node roles
2. **Cluster Bootstrapping**: Automated Talos cluster initialization
3. **Node Management**: Add/remove nodes from existing clusters
4. **Upgrades**: Talos and Kubernetes version upgrades
5. **PXE Boot Integration**: Network-based Talos installation

### Talos-Specific Features
1. **Security Configuration**: Certificate management and encryption
2. **Network Policies**: CNI configuration and network security
3. **Storage Integration**: CSI driver configuration
4. **Load Balancer Integration**: Automatic LB configuration for API server
5. **Monitoring Setup**: Pre-configured observability stack

### Integration with Existing Features
1. **Cluster Provisioning API**: Extend existing cluster provisioning
2. **VM Management**: Leverage existing VM creation and management
3. **Network Management**: Integrate with existing VLAN/networking
4. **Storage Management**: Use existing storage provisioning
5. **Monitoring**: Integrate with existing monitoring systems

## Implementation Plan

### Phase 1: Core Talos Infrastructure (Priority: HIGH)

#### Step 1.1: Talos Machine Config Service
**File to create:** `src/main/java/com/coffeesprout/service/TalosMachineConfigService.java`

Key methods:
- `generateControlPlaneConfig(ClusterConfig cluster, NodeConfig node)`
- `generateWorkerConfig(ClusterConfig cluster, NodeConfig node)`  
- `generateBootstrapConfig(ClusterConfig cluster)`
- `validateMachineConfig(String config)`
- `applyMachineConfig(int vmId, String config, String ticket)`

#### Step 1.2: Talos Cluster Service
**File to create:** `src/main/java/com/coffeesprout/service/TalosClusterService.java`

Key methods:
- `bootstrapCluster(TalosClusterConfig config, String ticket)`
- `joinNode(String clusterEndpoint, NodeConfig node, String ticket)`
- `removeNode(String nodeId, String ticket)`
- `upgradeCluster(String version, UpgradeStrategy strategy, String ticket)`
- `getClusterHealth(String clusterEndpoint, String ticket)`

#### Step 1.3: Talos API Client
**File to create:** `src/main/java/com/coffeesprout/client/TalosClient.java`

Features:
- Native Talos API integration
- Certificate-based authentication
- Cluster state management
- Node lifecycle operations
- Real-time cluster monitoring

### Phase 2: PXE Boot Integration (Priority: HIGH)

#### Step 2.1: Talos PXE Support  
**File to create:** `src/main/java/com/coffeesprout/service/platform/TalosPXESupport.java`

Key methods:
- `downloadTalosBootFiles(String version, String ticket)`
- `generatePXEConfigs(List<TalosNode> nodes)`
- `createTalosBootMenu(TalosNode node, MachineConfig config)`
- `serveTalosMachineConfigs(HttpServletRequest request)`

#### Step 2.2: Talos Boot File Manager
**File to create:** `src/main/java/com/coffeesprout/service/TalosBootFileManager.java`

Features:
- Automatic Talos ISO download and extraction
- PXE file organization (kernel, initramfs, rootfs)
- Machine config serving over HTTP
- Boot file caching and validation

### Phase 3: Data Models and Configuration (Priority: HIGH)

#### Step 3.1: Talos Configuration Models
**Files to create:**
- `src/main/java/com/coffeesprout/model/talos/TalosClusterConfig.java`
- `src/main/java/com/coffeesprout/model/talos/MachineConfig.java` 
- `src/main/java/com/coffeesprout/model/talos/TalosNode.java`
- `src/main/java/com/coffeesprout/model/talos/ClusterSecurityConfig.java`

```java
public record TalosClusterConfig(
    String clusterName,
    String kubernetesVersion,
    String talosVersion,
    ClusterSecurityConfig security,
    NetworkConfig network,
    List<TalosNode> controlPlane,
    List<TalosNode> workers,
    Map<String, Object> additionalConfig
) {}

public record TalosNode(
    String hostname,
    String ipAddress,
    String macAddress,
    NodeRole role,  // CONTROL_PLANE, WORKER
    MachineConfig config,
    String targetHost,  // Proxmox node
    Map<String, String> labels
) {}

public record ClusterSecurityConfig(
    String clusterSecret,
    CertificateConfig certificates,
    EncryptionConfig encryption,
    boolean secureBootEnabled
) {}
```

### Phase 4: REST API Implementation (Priority: HIGH)

#### Step 4.1: Talos Cluster Endpoints
**File to create:** `src/main/java/com/coffeesprout/api/TalosResource.java`

Endpoints:
- `POST /api/v1/clusters/talos` - Create Talos cluster
- `GET /api/v1/clusters/talos/{clusterId}` - Get cluster status  
- `PUT /api/v1/clusters/talos/{clusterId}/nodes` - Add/remove nodes
- `POST /api/v1/clusters/talos/{clusterId}/upgrade` - Upgrade cluster
- `GET /api/v1/clusters/talos/{clusterId}/kubeconfig` - Get kubeconfig

#### Step 4.2: Talos Machine Config Endpoints
**File to create:** `src/main/java/com/coffeesprout/api/TalosMachineConfigResource.java`

Endpoints:
- `POST /api/v1/talos/configs/generate` - Generate machine configs
- `GET /api/v1/talos/configs/{nodeId}` - Get node machine config
- `PUT /api/v1/talos/configs/{nodeId}` - Update machine config
- `POST /api/v1/talos/configs/validate` - Validate config

### Phase 5: Advanced Features (Priority: MEDIUM)

#### Step 5.1: Talos Extension Support
**File to create:** `src/main/java/com/coffeesprout/service/TalosExtensionService.java`

Features:
- System extension management (CNI, CSI drivers)
- Custom extension deployment
- Extension dependency resolution
- Extension lifecycle management

#### Step 5.2: Talos Upgrade Service
**File to create:** `src/main/java/com/coffeesprout/service/TalosUpgradeService.java`

Features:
- Rolling upgrade orchestration
- Pre-upgrade validation
- Rollback capabilities
- Upgrade progress monitoring

### Phase 6: Security and Observability (Priority: MEDIUM)

#### Step 6.1: Talos Security Manager
**File to create:** `src/main/java/com/coffeesprout/service/TalosSecurityManager.java`

Features:
- Certificate lifecycle management
- Cluster secret rotation
- Security policy enforcement
- Compliance reporting

#### Step 6.2: Talos Monitoring Integration
**File to create:** `src/main/java/com/coffeesprout/service/TalosMonitoringService.java`

Features:
- Built-in metrics collection
- Health check automation
- Alert rule configuration
- Dashboard generation

## API Examples

### Deploy Basic Talos Cluster
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d '{
    "name": "talos-prod",
    "type": "TALOS", 
    "bootMethod": "PXE",
    "talosVersion": "1.9.2",
    "kubernetesVersion": "1.32.0",
    "nodeGroups": [
      {
        "name": "controlplane",
        "role": "CONTROL_PLANE", 
        "count": 3,
        "template": {
          "cores": 4,
          "memoryMB": 8192,
          "disks": [{
            "interfaceType": "SCSI",
            "slot": 0,
            "sizeGB": 100,
            "ssd": true
          }]
        }
      },
      {
        "name": "workers",
        "role": "WORKER",
        "count": 3,
        "template": {
          "cores": 8,
          "memoryMB": 16384,
          "disks": [{
            "interfaceType": "SCSI", 
            "slot": 0,
            "sizeGB": 200,
            "ssd": true
          }]
        }
      }
    ],
    "security": {
      "encryptionEnabled": true,
      "secureBootEnabled": true
    },
    "network": {
      "cni": "cilium",
      "serviceSubnet": "10.96.0.0/12",
      "podSubnet": "10.244.0.0/16"
    }
  }'
```

### Add Worker Node to Existing Cluster  
```bash
curl -X PUT http://localhost:8080/api/v1/clusters/talos/talos-prod/nodes \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD",
    "nodes": [
      {
        "hostname": "worker-4",
        "role": "WORKER",
        "targetHost": "hv3",
        "template": {
          "cores": 8,
          "memoryMB": 16384
        }
      }
    ]
  }'
```

### Upgrade Talos Cluster
```bash
curl -X POST http://localhost:8080/api/v1/clusters/talos/talos-prod/upgrade \
  -H "Content-Type: application/json" \
  -d '{
    "talosVersion": "1.9.3",
    "kubernetesVersion": "1.32.1",
    "strategy": "ROLLING",
    "maxUnavailable": 1
  }'
```

### Generate Machine Config
```bash
curl -X POST http://localhost:8080/api/v1/talos/configs/generate \
  -H "Content-Type: application/json" \
  -d '{
    "clusterName": "talos-prod",
    "nodeRole": "CONTROL_PLANE",
    "nodeConfig": {
      "hostname": "control-1",
      "ipAddress": "10.1.107.11"
    }
  }'
```

## Technical Implementation Details

### Talos Machine Config Structure
```yaml
version: v1alpha1
machine:
  type: controlplane  # or worker
  token: <cluster-join-token>
  ca:
    crt: <base64-encoded-ca-cert>
    key: <base64-encoded-ca-key>
  certSANs:
    - 10.1.107.1  # Load balancer IP
  kubelet:
    image: ghcr.io/siderolabs/kubelet:v1.32.0
  network:
    interfaces:
      - interface: eth0
        dhcp: true
  install:
    disk: /dev/sda
    image: ghcr.io/siderolabs/installer:v1.9.2
cluster:
  name: talos-prod
  endpoint: https://10.1.107.1:6443
  network:
    cni:
      name: cilium
```

### PXE Boot Integration
- Download Talos installer image
- Extract kernel, initramfs for network boot
- Generate machine configs for each node
- Create MAC-aware GRUB menus
- Serve configs over HTTP during boot

### Cluster Bootstrap Process
1. **Generate Configs**: Create machine configs for all nodes
2. **PXE Deploy**: Network boot all nodes with their configs  
3. **Initialize**: Bootstrap first control plane node
4. **Join**: Add remaining control plane and worker nodes
5. **Validate**: Verify cluster health and readiness

## Success Criteria

1. ✅ Talos clusters can be provisioned via Moxxie API
2. ✅ PXE boot works reliably for Talos nodes
3. ✅ Machine configs are generated correctly for each role
4. ✅ Cluster bootstrapping completes successfully
5. ✅ Nodes can be added/removed from running clusters
6. ✅ Talos and Kubernetes upgrades work smoothly
7. ✅ Security features (encryption, certificates) function properly
8. ✅ Integration with existing Moxxie features works seamlessly
9. ✅ Comprehensive monitoring and observability available
10. ✅ All unit and integration tests pass

## Benefits of Talos for Kubernetes

1. **Security**: Minimal attack surface, no SSH/shell access
2. **Reliability**: Immutable OS with atomic updates
3. **Simplicity**: Purpose-built for Kubernetes, fewer components
4. **Performance**: Optimized for container workloads
5. **Automation**: API-driven configuration and management
6. **Compliance**: Built-in security and audit capabilities
7. **Cloud-Native**: First-class support for cloud deployments

## Future Enhancements

1. **Multi-Cluster Management**: Fleet management for multiple Talos clusters
2. **GitOps Integration**: Cluster configuration via Git repositories
3. **Service Mesh**: Automatic Istio/Linkerd deployment
4. **Storage Classes**: Automatic CSI driver configuration
5. **Backup Integration**: Automated etcd and persistent volume backups
6. **Compliance Scanning**: CIS benchmark and security policy enforcement
7. **Edge Computing**: ARM64 support for edge deployments
8. **Disaster Recovery**: Automated cluster backup and restore

## Risk Mitigation

1. **Boot Failures**: Comprehensive PXE troubleshooting and recovery
2. **Network Partitions**: Split-brain prevention and cluster recovery
3. **Certificate Expiration**: Automated certificate rotation
4. **Upgrade Failures**: Rollback mechanisms and validation gates
5. **Data Loss**: etcd backup automation and validation
6. **Security Breaches**: Immutable infrastructure and rapid recovery

## Integration with Existing Moxxie Features

1. **VM Management**: Leverage existing VM provisioning
2. **Network Management**: Use VLAN and network isolation features  
3. **Storage Management**: Integrate with existing storage provisioning
4. **Monitoring**: Extend existing monitoring for Talos metrics
5. **Safety Features**: Apply safe mode restrictions to Talos clusters
6. **Audit Logging**: Full audit trail for all Talos operations

## Notes

- Talos is particularly well-suited for production Kubernetes workloads
- No SSH access means all management must be via Talos API
- Machine configs are immutable and version-controlled
- Cluster certificates have automatic rotation capabilities
- PXE boot significantly simplifies Talos deployment
- Security is built-in by design, not an afterthought