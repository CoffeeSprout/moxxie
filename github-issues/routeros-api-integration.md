# Integrate RouterOS API for Network Automation

## Overview
Add MikroTik RouterOS API client to enable automated network provisioning directly from Moxxie.

## Requirements

### RouterOS API Client
- [ ] Java client library for RouterOS API
- [ ] SSL/TLS support (port 8729)
- [ ] Connection pooling
- [ ] Error handling and retry logic
- [ ] Async/reactive support

### Configuration
```yaml
moxxie:
  routers:
    dc1:
      host: 10.0.0.1
      port: 8729
      username: ${ROUTER_API_USER}
      password: ${ROUTER_API_PASSWORD}
      ssl: true
    dc2:
      host: 10.0.0.2
      port: 8729
      username: ${ROUTER_API_USER}
      password: ${ROUTER_API_PASSWORD}
      ssl: true
      qinq:
        enabled: true
        serviceVlan: 1000
```

### Core Operations
```java
public interface RouterOSService {
    // VLAN management
    void createVlan(String datacenter, int vlanId, String name);
    void deleteVlan(String datacenter, int vlanId);
    
    // IP address management
    void addIpAddress(String datacenter, String address, String interfaceName);
    void removeIpAddress(String datacenter, String address);
    
    // DHCP management
    void createDhcpPool(String datacenter, String poolName, String ranges);
    void createDhcpServer(String datacenter, String vlan, String pool);
    
    // Firewall rules
    void addFirewallRule(String datacenter, FirewallRule rule);
    
    // Monitoring
    RouterStatus getStatus(String datacenter);
    List<String> getRoutes(String datacenter);
}
```

### Q-in-Q Support for DC2
```java
// Special handling for DC2
if (datacenter.equals("DC2") && qinqEnabled) {
    // Create customer VLAN inside service VLAN
    api.execute("/interface/vlan/add",
        "name", vlanName,
        "interface", "svlan" + serviceVlan,
        "vlan-id", vlanId);
} else {
    // Standard VLAN creation
    api.execute("/interface/vlan/add",
        "name", vlanName,
        "interface", "bridge",
        "vlan-id", vlanId);
}
```

## Implementation Notes
- Consider using existing library: https://github.com/geeeyetee/routeros-api-java
- Must handle Q-in-Q differences between DCs
- Implement connection health checks
- Add comprehensive logging
- Create abstraction layer for future vendor support

## Testing Requirements
- [ ] Unit tests with mocked API
- [ ] Integration tests with CHR (Cloud Hosted Router)
- [ ] Test Q-in-Q configuration
- [ ] Test error scenarios (connection loss, auth failure)

## Acceptance Criteria
- [ ] Can provision VLANs on both routers
- [ ] Handles DC2 Q-in-Q correctly  
- [ ] Proper error handling and logging
- [ ] Configuration is environment-specific
- [ ] Includes usage documentation

Labels: `enhancement`, `networking`, `integration`
Milestone: Network Integration