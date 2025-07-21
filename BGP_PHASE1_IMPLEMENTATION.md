# Phase 1: BGP Implementation Guide for MikroTik Infrastructure

## Overview

This guide details how to implement BGP routing between MikroTik switches while maintaining your existing VLAN architecture. This approach provides better redundancy, load balancing, and scalability without the complexity of overlay networks.

## Quick Clarification on Cilium BGP

Yes, Cilium supports BGP! Since version 1.10, Cilium can:
- Advertise PodCIDRs via BGP
- Advertise LoadBalancer service IPs
- Replace MetalLB in many scenarios
- Peer with your infrastructure routers

This makes Phase 1 even more valuable - you're building infrastructure that Cilium can integrate with directly.

## Architecture Overview

```
                    [Internet/Upstream]
                           |
                    [Edge Router]
                      10.0.0.254
                           |
        ┌──────────────────┴──────────────────┐
        |              iBGP Mesh              |
        |                                     |
   [Switch1]                             [Switch2]
   10.0.0.1                              10.0.0.2
   AS 65000                              AS 65000
        |                                     |
   VLANs 1-2000                          VLANs 2001-4000
   172.16.0.0/20                         172.16.16.0/20
        |                                     |
   [Hypervisors]                         [Hypervisors]
```

## Benefits of This Approach

1. **No Overlay Complexity** - Keep using VLANs as-is
2. **Active-Active Routing** - Both switches actively route traffic
3. **Automatic Failover** - BGP handles switch failures
4. **Load Balancing** - ECMP distributes traffic
5. **Cilium Ready** - Can peer with Cilium BGP later
6. **Simple to Debug** - Just VLANs + standard routing

## Implementation Steps

### Step 1: IP Addressing Plan

```bash
# Loopbacks (BGP Router IDs)
Switch1: 10.0.0.1/32
Switch2: 10.0.0.2/32
Switch3: 10.0.0.3/32 (future)

# Point-to-Point Links (if not using single broadcast domain)
Switch1-Switch2: 10.0.12.0/31 (.0 and .1)
Switch1-Switch3: 10.0.13.0/31
Switch2-Switch3: 10.0.23.0/31

# Management Network
10.0.100.0/24 (out-of-band)
```

### Step 2: Basic BGP Configuration

#### Switch 1 Configuration

```bash
# 1. System Identity
/system identity set name=switch1

# 2. Loopback Interface
/interface bridge
add name=lo0 comment="BGP Loopback"
/ip address
add address=10.0.0.1/32 interface=lo0

# 3. Enable BGP
/routing bgp template
add as=65000 name=ibgp-template router-id=10.0.0.1 \
    routing-table=main disabled=no

# 4. BGP Peer to Switch2
/routing bgp connection
add connect=yes disabled=no local.role=ibgp \
    name=to-switch2 output.network=bgp-networks \
    remote.address=10.0.0.2/32 remote.as=65000 \
    router-id=10.0.0.1 routing-table=main \
    templates=ibgp-template

# 5. Advertise Networks
/routing filter rule
add chain=bgp-networks disabled=no rule=\
    "if (dst in 172.16.0.0/20) {accept}"

# 6. ECMP Configuration
/routing bgp template
set ibgp-template address-families=ip use-bfd=yes \
    nexthop-choice=force-self
```

#### Switch 2 Configuration

```bash
# Mirror configuration with appropriate IPs
/interface bridge
add name=lo0
/ip address
add address=10.0.0.2/32 interface=lo0

/routing bgp connection
add connect=yes disabled=no local.role=ibgp \
    name=to-switch1 output.network=bgp-networks \
    remote.address=10.0.0.1/32 remote.as=65000 \
    router-id=10.0.0.2 routing-table=main
```

### Step 3: VLAN Distribution Strategy

Instead of stretching all VLANs across all switches, distribute them:

```bash
# Switch1: Primary for VLANs 1-2000
/interface vlan
add interface=bridge name=vlan101 vlan-id=101
/ip address
add address=172.16.1.1/24 interface=vlan101

# Switch2: Primary for VLANs 2001-4000  
/interface vlan
add interface=bridge name=vlan2001 vlan-id=2001
/ip address
add address=172.17.1.1/24 interface=vlan2001
```

### Step 4: Route Health Injection

Only advertise routes for healthy VLANs:

```bash
# Create tracking script
/system script
add name=check-vlan-health source={
    :local vlanUp [/interface get vlan101 running]
    :if ($vlanUp) do={
        /routing filter rule set [find comment="vlan101-route"] disabled=no
    } else={
        /routing filter rule set [find comment="vlan101-route"] disabled=yes
    }
}

# Schedule health checks
/system scheduler
add interval=10s name=vlan-health on-event=check-vlan-health
```

### Step 5: Optimal Path Selection

Configure BGP attributes for traffic engineering:

```bash
# Prefer local VLANs
/routing filter rule
add chain=bgp-networks comment="Prefer local VLANs" disabled=no \
    rule="if (dst in 172.16.0.0/20) {set bgp-local-pref 200; accept}"

# Lower preference for remote VLANs
add chain=bgp-in disabled=no \
    rule="if (dst in 172.17.0.0/20) {set bgp-local-pref 100; accept}"
```

### Step 6: BFD for Fast Failover

Enable Bidirectional Forwarding Detection:

```bash
# Enable BFD on interfaces
/routing bfd configuration
add disabled=no interfaces=ether1 min-rx=100ms min-tx=100ms

# Link to BGP sessions
/routing bgp connection
set to-switch2 use-bfd=yes
```

## Integration with Moxxie

### Network Allocation Updates

```java
@Entity
public class NetworkAssignment {
    @Id
    private Long id;
    
    private String clientId;
    private Integer vlanId;
    private String subnet;          // "172.16.1.0/24"
    private String primarySwitch;   // "switch1"
    private String backupSwitch;    // "switch2"
    private Boolean bgpAdvertised;
    
    @ElementCollection
    private List<String> advertisedFrom; // Tracks which switches advertise this network
}
```

### Moxxie API Enhancements

```bash
# Get optimal switch for new VM
GET /api/v1/network-fabric/optimal-switch?vlan=101
{
  "primarySwitch": "switch1",
  "currentLoad": 45,
  "pathCost": 10
}

# Trigger BGP advertisement
POST /api/v1/network-fabric/advertise
{
  "subnet": "172.16.1.0/24",
  "switches": ["switch1", "switch2"],
  "preference": {
    "switch1": 200,
    "switch2": 100
  }
}
```

## Monitoring and Troubleshooting

### Key Commands

```bash
# BGP Status
/routing bgp session print
/routing bgp advertisements print

# Route Table
/ip route print where bgp

# Traffic distribution
/interface monitor-traffic ether1,ether2

# BFD Status
/routing bfd session print
```

### What to Monitor

1. **BGP Session State** - Should be "Established"
2. **Route Count** - Each switch should see all networks
3. **Path Selection** - Verify ECMP is working
4. **BFD State** - Should show "Up"

## Migration Playbook

### Week 1: Lab Testing
- Set up two test switches
- Configure basic BGP
- Test failover scenarios

### Week 2: Production Prep
- Document all VLANs
- Plan IP assignments
- Create rollback scripts

### Week 3: Pilot Deployment
- Start with non-critical VLANs
- Monitor for 48 hours
- Gradually add more networks

### Week 4: Full Deployment
- Migrate remaining VLANs
- Enable BFD
- Update monitoring

## Future Integration: Cilium BGP

Once Phase 1 is stable, you can integrate Cilium:

```yaml
# Cilium BGP configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: bgp-config
  namespace: kube-system
data:
  config.yaml: |
    peers:
      - peer-address: 10.0.0.1
        peer-asn: 65000
        my-asn: 65100
    address-pools:
      - name: default
        protocol: bgp
        addresses:
          - 172.20.0.0/24  # LoadBalancer IPs
```

## Common Pitfalls to Avoid

1. **Don't advertise all routes by default** - Be selective
2. **Don't forget BFD** - 3-second BGP timers are too slow
3. **Don't use same VLAN on all switches** - Defeats the purpose
4. **Don't ignore MTU** - Account for any future overlay needs
5. **Don't skip monitoring** - BGP issues can be subtle

## Success Criteria

- [ ] BGP sessions established between all switches
- [ ] Each VLAN accessible from any switch
- [ ] Failover completes in <1 second
- [ ] Traffic distributed across links
- [ ] Monitoring alerts configured
- [ ] Documentation updated
- [ ] Team trained on BGP basics

## Next Steps

After Phase 1 is stable (2-3 months), consider:
- Phase 2: VRF-Lite for client isolation
- Phase 3: Cilium BGP integration
- Phase 4: IPv6 deployment
- Future: EVPN when MikroTik support matures

## Resources

- [MikroTik BGP Manual](https://help.mikrotik.com/docs/display/ROS/BGP)
- [RFC 7938 - BGP in Data Centers](https://datatracker.ietf.org/doc/html/rfc7938)
- [Cilium BGP Documentation](https://docs.cilium.io/en/stable/network/bgp/)