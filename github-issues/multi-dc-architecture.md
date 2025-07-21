# Implement Multi-DC Architecture Support

## Overview
Moxxie needs to be datacenter-aware for proper resource allocation, network provisioning, and cross-DC operations.

## Requirements

### DC-Aware Configuration
```java
@ConfigMapping(prefix = "moxxie.datacenters")
public interface DatacenterConfig {
    Map<String, DatacenterSettings> centers();
    
    interface DatacenterSettings {
        String name();
        String location();
        String proxmoxUrl();
        String routerAddress();
        NetworkSettings network();
        ResourceLimits limits();
    }
    
    interface NetworkSettings {
        String ipRange();        // "172.16.0.0/16" 
        String vlanRange();      // "100-2000"
        boolean qinqEnabled();
        Integer serviceVlan();
    }
}
```

### Database Updates
```java
@Entity
public class VMRecord {
    // Existing fields...
    
    @Column(nullable = false)
    private String datacenter;
    
    private String primaryDC;    // For failover tracking
    private String currentDC;    // Where it's running now
}
```

### API Enhancements

#### VM Creation with DC Preference
```json
POST /api/v1/vms
{
  "name": "web-server",
  "datacenter": "DC1",        // Required
  "failoverDC": "DC2",        // Optional
  "cores": 4,
  "memoryMB": 8192,
  "affinityRules": {
    "preferSameDC": ["db-server"],
    "requireDifferentDC": ["web-server-2"]
  }
}
```

#### Cross-DC Operations
```
POST /api/v1/vms/{id}/migrate-dc
{
  "targetDatacenter": "DC2",
  "migrationType": "offline",  // or "live" when supported
  "networkMapping": {
    "vlan100": "vlan100"      // Map networks between DCs
  }
}
```

#### DC-Aware Queries
```
GET /api/v1/vms?datacenter=DC1
GET /api/v1/datacenters/DC1/statistics
GET /api/v1/datacenters/DC1/available-resources
```

### Service Layer Updates
```java
@ApplicationScoped
public class DatacenterAwareVMService {
    
    public VMResponse createVM(CreateVMRequest request) {
        // Validate DC exists and has capacity
        DatacenterSettings dc = validateDatacenter(request.datacenter());
        
        // Allocate network in correct DC
        NetworkAllocation network = ipamService.allocateNetwork(
            request.datacenter(), 
            request.clientId()
        );
        
        // Create VM with DC-specific settings
        return vmService.createVM(request, dc, network);
    }
    
    public List<DatacenterCapacity> getCapacity() {
        return datacenters.stream()
            .map(dc -> calculateCapacity(dc))
            .toList();
    }
}
```

### Proxmox API Router
```java
@ApplicationScoped
public class MultiDCProxmoxRouter {
    
    @Inject
    @RestClient
    @DatacenterQualifier("DC1")
    ProxmoxClient dc1Client;
    
    @Inject
    @RestClient  
    @DatacenterQualifier("DC2")
    ProxmoxClient dc2Client;
    
    public ProxmoxClient getClient(String datacenter) {
        return switch(datacenter) {
            case "DC1" -> dc1Client;
            case "DC2" -> dc2Client;
            default -> throw new IllegalArgumentException();
        };
    }
}
```

## Implementation Phases

### Phase 1: Basic DC Awareness
- [ ] Add datacenter field to all resources
- [ ] Update APIs to filter by DC
- [ ] Configure per-DC Proxmox endpoints

### Phase 2: Network Integration  
- [ ] DC-specific network allocation
- [ ] Handle DC-specific settings (Q-in-Q for DC2)
- [ ] Update VM creation for correct DC

### Phase 3: Cross-DC Features
- [ ] VM migration between DCs
- [ ] Capacity planning across DCs
- [ ] Failover tracking

## Testing Requirements
- [ ] Test with multiple Proxmox endpoints
- [ ] Verify network isolation between DCs
- [ ] Test DC failover scenarios
- [ ] Performance testing for cross-DC queries

## Acceptance Criteria
- [ ] VMs can be created in specific DCs
- [ ] Network allocation is DC-aware
- [ ] Can query resources by DC
- [ ] DC2 Q-in-Q handled transparently
- [ ] Documentation includes DC setup guide

Labels: `enhancement`, `architecture`, `multi-dc`
Milestone: Multi-DC Support