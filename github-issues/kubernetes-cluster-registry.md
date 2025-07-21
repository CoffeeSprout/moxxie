# Add Kubernetes Cluster Registry and BGP Support

## Overview
Track Kubernetes clusters managed by Moxxie, including their network assignments and BGP peering configuration for Cilium.

## Requirements

### Cluster Registry
```java
@Entity
public class KubernetesCluster {
    @Id
    private String clusterId;
    private String name;
    private String clientId;
    private String datacenter;
    
    // Network settings
    private Integer vlanId;
    private String nodeSubnet;      // "172.16.100.0/24"
    private String podCIDR;         // "10.101.0.0/16"
    private String serviceCIDR;     // "10.201.0.0/16"
    
    // BGP settings
    private Integer bgpASN;         // 65101
    private String bgpPeerIP;       // "172.16.100.10"
    private boolean bgpEnabled;
    
    // Cluster details
    private String kubernetesVersion;
    private String cni;             // "cilium", "calico", etc
    private Integer masterNodes;
    private Integer workerNodes;
    
    private LocalDateTime created;
    private LocalDateTime updated;
}
```

### API Endpoints
```
POST   /api/v1/k8s-clusters          - Register new cluster
GET    /api/v1/k8s-clusters          - List all clusters
GET    /api/v1/k8s-clusters/{id}     - Get cluster details
PUT    /api/v1/k8s-clusters/{id}     - Update cluster
DELETE /api/v1/k8s-clusters/{id}     - Deregister cluster

GET    /api/v1/k8s-clusters/{id}/bgp-config - Generate BGP config
GET    /api/v1/k8s-clusters/{id}/cilium-config - Generate Cilium config
```

### BGP Configuration Generator

#### Router Side
```java
public String generateRouterBGPConfig(KubernetesCluster cluster) {
    return String.format("""
        /routing bgp connection
        add name=to-k8s-%s \\
            remote.address=%s \\
            remote.as=%d \\
            local.as=65001 \\
            multihop=yes \\
            address-families=ip \\
            comment="Kubernetes cluster %s"
        """, 
        cluster.getClusterId(),
        cluster.getBgpPeerIP(),
        cluster.getBgpASN(),
        cluster.getName()
    );
}
```

#### Cilium Side
```java
public String generateCiliumConfig(KubernetesCluster cluster) {
    return String.format("""
        apiVersion: cilium.io/v2alpha1
        kind: CiliumBGPPeeringPolicy
        metadata:
          name: moxxie-bgp-peering
        spec:
          nodeSelector:
            matchLabels:
              bgp: "enabled"
          virtualRouters:
          - localASN: %d
            exportPodCIDR: true
            neighbors:
            - peerAddress: %s/32
              peerASN: 65001
              connectRetryTime: 30s
        """,
        cluster.getBgpASN(),
        getRouterIP(cluster.getDatacenter(), cluster.getVlanId())
    );
}
```

### Integration with VM Creation
```java
// When creating K8s nodes, automatically:
// 1. Check if cluster exists
// 2. Use correct VLAN
// 3. Assign IPs from cluster subnet
// 4. Tag appropriately

@POST
@Path("/vms")
public VMResponse createVM(CreateVMRequest request) {
    if (request.tags().contains("k8s-node")) {
        KubernetesCluster cluster = findClusterByTags(request.tags());
        
        // Ensure correct network
        request = request.withNetwork(
            new NetworkConfig("virtio", getBridge(cluster.getVlanId()))
        );
        
        // Assign IP from cluster range
        String ip = ipamService.allocateFromSubnet(cluster.getNodeSubnet());
        request = request.withIPConfig(formatIPConfig(ip, cluster));
    }
    
    return vmService.createVM(request);
}
```

### ASN Allocation Strategy
```java
public class ASNAllocator {
    private static final int BASE_ASN = 65100;
    private static final int MAX_ASN = 65500;
    
    public int allocateASN(String datacenter) {
        // DC1: 65100-65299
        // DC2: 65300-65499
        int dcOffset = datacenter.equals("DC1") ? 0 : 200;
        
        int nextASN = repository.findMaxASNForDatacenter(datacenter)
            .orElse(BASE_ASN + dcOffset);
            
        if (nextASN >= BASE_ASN + dcOffset + 200) {
            throw new ResourceExhaustedException("No more ASNs available");
        }
        
        return nextASN + 1;
    }
}
```

## Implementation Notes
- ASN allocation must be unique per cluster
- Consider reserving ASN ranges per customer
- BGP peer IP is typically first node in cluster
- Support both Cilium and Calico BGP modes
- Track BGP session status if possible

## Testing
- [ ] Test cluster registration
- [ ] Verify BGP config generation
- [ ] Test ASN allocation and limits
- [ ] Integration test with router API
- [ ] Test Cilium config format

## Acceptance Criteria
- [ ] Can register K8s clusters with network details
- [ ] Generates valid RouterOS BGP configuration
- [ ] Generates valid Cilium BGP configuration
- [ ] ASN allocation prevents conflicts
- [ ] VM creation respects cluster settings
- [ ] API documentation complete

Labels: `enhancement`, `kubernetes`, `bgp`, `networking`
Milestone: Kubernetes Integration