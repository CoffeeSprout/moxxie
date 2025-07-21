# Add IPAM (IP Address Management) Functionality

## Overview
Moxxie needs built-in IPAM capabilities to track network allocations, IP assignments, and prevent conflicts across multiple datacenters.

## Requirements

### Core IPAM Features
- [ ] Track VLAN allocations per datacenter
- [ ] Manage IP subnet assignments
- [ ] Track individual IP allocations
- [ ] Prevent IP conflicts
- [ ] Support IPv4 and IPv6

### Database Schema
```java
@Entity
public class NetworkAllocation {
    @Id
    private Long id;
    private String datacenter;
    private Integer vlanId;
    private String subnet;      // "172.16.100.0/24"
    private String gateway;     // "172.16.100.1"
    private String clientId;
    private String purpose;
    private boolean qinq;      // DC2 Q-in-Q encapsulation
    private Integer serviceVlan; // DC2 S-VLAN if applicable
    private LocalDateTime allocated;
}

@Entity  
public class IPAllocation {
    @Id
    private String ipAddress;
    private String hostname;
    private String macAddress;
    private Long networkAllocationId;
    private String type; // "vm", "gateway", "reserved"
    private LocalDateTime allocated;
}
```

### API Endpoints
```
POST   /api/v1/networks/allocate     - Allocate new network
GET    /api/v1/networks              - List all networks
GET    /api/v1/networks/{id}         - Get network details
DELETE /api/v1/networks/{id}         - Release network
GET    /api/v1/networks/{id}/ips     - List IPs in network
POST   /api/v1/networks/{id}/ips/next - Get next available IP
```

## Implementation Notes
- Start with PostgreSQL for storage
- Consider IP range calculations (use IPAddress library)
- Must handle Q-in-Q for DC2
- Reserve first IP for gateway, last for broadcast

## Acceptance Criteria
- [ ] Can allocate networks without conflicts
- [ ] Tracks all IP assignments
- [ ] Provides next available IP
- [ ] Supports both DCs with different schemes
- [ ] Has API documentation

Labels: `enhancement`, `networking`, `ipam`
Milestone: Network Integration