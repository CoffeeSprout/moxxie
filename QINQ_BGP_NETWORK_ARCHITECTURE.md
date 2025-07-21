# Q-in-Q + BGP Network Architecture Guide

## Executive Summary

This guide outlines a network architecture that gives you full control over your VLANs across two datacenters, even when you don't control the switches in DC2. By using Q-in-Q (802.1ad) and BGP routers, you'll have a scalable, vendor-agnostic solution that grows with your needs.

**Key Benefits:**
- Full control over 4094 VLANs in each DC
- Consistent VLAN numbering across sites
- Future-proof BGP routing
- Easy migration to your own switches later
- No vendor lock-in

## Table of Contents
1. [Understanding Q-in-Q](#understanding-q-in-q)
2. [Architecture Overview](#architecture-overview)
3. [Hardware Recommendations](#hardware-recommendations)
4. [Configuration Guide](#configuration-guide)
5. [Migration Roadmap](#migration-roadmap)
6. [Operational Procedures](#operational-procedures)
7. [Troubleshooting](#troubleshooting)

## Understanding Q-in-Q

### What Is Q-in-Q?

Q-in-Q (802.1ad) is like a shipping container for VLANs. The datacenter gives you one "container" (Service VLAN), and inside it, you can pack your own VLANs (Customer VLANs).

```
Normal VLAN:
[Ethernet Frame] → [VLAN Tag: 100] → [Your Data]

Q-in-Q:
[Ethernet Frame] → [S-VLAN: 1000] → [C-VLAN: 100] → [Your Data]
                    ↑                  ↑
                    Provider's         Your VLAN
                    VLAN (Outer)       (Inner)
```

### Why Q-in-Q Is Perfect for Your Situation

1. **VLAN Independence**: Use VLAN 100 in both DCs without conflict
2. **Full Range**: Get all 4094 VLANs to yourself
3. **Provider Isolation**: Your traffic is invisible to other customers
4. **Simple Migration**: When you get your own switches, just remove the outer tag

## Architecture Overview

### Current State
```
DC1 (Your Control)                    DC2 (Managed)
┌─────────────────┐                  ┌──────────────────┐
│   FS Switches   │                  │  Cisco Switches  │
│  (Stacked)      │                  │  (Stacked)       │
│                 │                  │                  │
│  VLANs 1-4094   │                  │  S-VLAN 1000     │
└────────┬────────┘                  │  ├─C-VLAN 1-4094 │
         │                           └─────────┬────────┘
         │                                     │
     Hypervisors                           Hypervisors
```

### Proposed Architecture
```
DC1 (Your Control)                    DC2 (Managed)
┌─────────────────┐                  ┌──────────────────┐
│   FS Switches   │                  │  Cisco Switches  │
│  (Stacked)      │                  │  (Stacked)       │
└────────┬────────┘                  └────────┬─────────┘
         │                                     │
         │ VLANs                               │ Q-in-Q Trunk
         │                                     │ (S-VLAN 1000)
┌────────▼────────┐                  ┌────────▼─────────┐
│ MikroTik Router │◄────────────────►│ MikroTik Router  │
│   BGP AS65001   │      iBGP        │   BGP AS65001    │
└─────────────────┘   10G/40G Link   └──────────────────┘
```

### Logical View
```
Your Network Perspective:
┌──────────────────────────────────────────────────────┐
│                 Unified VLAN Space                   │
│                                                      │
│  VLAN 100: Production Web Servers (Both DCs)        │
│  VLAN 200: Database Servers (Both DCs)              │
│  VLAN 300: Kubernetes Nodes (Both DCs)              │
│  VLAN 400: Management Network (Both DCs)            │
└──────────────────────────────────────────────────────┘
                         │
                    BGP Routing
                    Handles Path
```

## Hardware Recommendations

### BGP Routers (One per DC)

#### Option 1: MikroTik CCR2004-12G-4S+ ($395)
- **Pros**: Affordable, 12x1G + 4x10G ports, full RouterOS
- **Cons**: Limited to 10G uplinks
- **Good for**: Current needs with moderate growth

#### Option 2: MikroTik CCR2216-1G-12XS-2XQ ($1995)
- **Pros**: 12x25G + 2x100G ports, serious routing capacity
- **Cons**: Higher cost
- **Good for**: Future-proofing, high-bandwidth applications

#### Option 3: MikroTik CCR2116-12G-4S+ ($795)
- **Pros**: Good middle ground, 12x1G + 4x10G
- **Cons**: Not as future-proof as CCR2216
- **Good for**: Balanced price/performance

### Recommended: CCR2116-12G-4S+ (Best Available Option)
- CCR2004 is sold out everywhere
- CCR2116 offers better performance at reasonable price (~€800-900)
- 16-core CPU handles BGP and future VXLAN easily
- 4x 10G ports are more than sufficient
- Available for immediate purchase

## Configuration Guide

### DC1 Router (With Your FS Switches)

```bash
# System identity
/system identity set name=dc1-bgp-router

# Physical interfaces
/interface ethernet
set sfp-sfpplus1 name=uplink-to-switches comment="To FS Stack"
set sfp-sfpplus2 name=link-to-dc2 comment="To DC2 Router"

# Create VLANs (standard 802.1Q)
/interface vlan
add name=vlan100-prod interface=uplink-to-switches vlan-id=100
add name=vlan200-db interface=uplink-to-switches vlan-id=200
add name=vlan300-k8s interface=uplink-to-switches vlan-id=300
add name=vlan400-mgmt interface=uplink-to-switches vlan-id=400

# IP addresses (DC1 uses 172.16.0.0/16)
/ip address
add address=172.16.100.1/24 interface=vlan100-prod
add address=172.16.200.1/24 interface=vlan200-db
add address=172.16.300.1/24 interface=vlan300-k8s
add address=172.16.400.1/24 interface=vlan400-mgmt

# Loopback for BGP
/interface bridge add name=loopback0
/ip address add address=10.255.1.1/32 interface=loopback0

# Inter-DC link
/ip address add address=10.255.255.1/30 interface=link-to-dc2
```

### DC2 Router (With Managed Cisco Switches + Q-in-Q)

```bash
# System identity
/system identity set name=dc2-bgp-router

# Physical interfaces
/interface ethernet
set sfp-sfpplus1 name=uplink-to-switches comment="To Cisco Stack (Q-in-Q)"
set sfp-sfpplus2 name=link-to-dc1 comment="To DC1 Router"

# CRITICAL: Accept Q-in-Q frames
/interface ethernet switch port
set sfp-sfpplus1 vlan-mode=secure vlan-header=add-if-missing

# Create Service VLAN interface
/interface vlan
add name=svlan1000 interface=uplink-to-switches vlan-id=1000 use-service-tag=yes

# Create Customer VLANs inside Service VLAN
/interface vlan
add name=vlan100-prod interface=svlan1000 vlan-id=100
add name=vlan200-db interface=svlan1000 vlan-id=200
add name=vlan300-k8s interface=svlan1000 vlan-id=300
add name=vlan400-mgmt interface=svlan1000 vlan-id=400

# IP addresses (DC2 uses 172.20.0.0/16)
/ip address
add address=172.20.100.1/24 interface=vlan100-prod
add address=172.20.200.1/24 interface=vlan200-db
add address=172.20.300.1/24 interface=vlan300-k8s
add address=172.20.400.1/24 interface=vlan400-mgmt

# Loopback for BGP
/interface bridge add name=loopback0
/ip address add address=10.255.2.1/32 interface=loopback0

# Inter-DC link
/ip address add address=10.255.255.2/30 interface=link-to-dc1
```

### BGP Configuration (Both Routers)

```bash
# BGP Template (same on both routers)
/routing bgp template
add as=65001 name=main-template router-id=10.255.1.1 \
    address-families=ip routing-table=main

# On DC1 Router
/routing bgp connection
add name=to-dc2 remote.address=10.255.255.2 remote.as=65001 \
    templates=main-template local.address=10.255.255.1

# On DC2 Router  
/routing bgp connection
add name=to-dc1 remote.address=10.255.255.1 remote.as=65001 \
    templates=main-template local.address=10.255.255.2

# Advertise local networks (DC1 example)
/routing filter rule
add chain=bgp-out disabled=no rule="if (dst in 172.16.0.0/16) {accept}"

# Advertise local networks (DC2 example)
/routing filter rule
add chain=bgp-out disabled=no rule="if (dst in 172.20.0.0/16) {accept}"
```

### VRRP for Gateway Redundancy (Future)

When you have multiple routers per DC:
```bash
/interface vrrp
add interface=vlan100-prod name=vrrp100 vrid=100 \
    priority=200 ip-address=172.16.100.1

# Lower priority on backup router
add interface=vlan100-prod name=vrrp100 vrid=100 \
    priority=100 ip-address=172.16.100.1
```

## Migration Roadmap

### Phase 1: Proof of Concept (Week 1-2)
1. **Order routers** - Start with CCR2004 for both DCs
2. **Lab test** - Set up Q-in-Q in lab environment
3. **Verify with provider** - Confirm S-VLAN assignment and port configs
4. **Document current VLANs** - Map all existing VLANs and their purposes

### Phase 2: DC2 Deployment (Week 3-4)
1. **Install DC2 router** - Connect to Cisco switches
2. **Configure Q-in-Q** - Test with single VLAN first
3. **Migrate one service** - Pick non-critical workload
4. **Monitor for 48 hours** - Ensure stability

### Phase 3: DC1 Deployment (Week 5-6)
1. **Install DC1 router** - Connect to FS switches
2. **Configure standard VLANs** - Match DC2 numbering
3. **Establish BGP** - Bring up inter-DC routing
4. **Test failover** - Verify traffic paths

### Phase 4: Progressive Migration (Week 7-12)
1. **Move services gradually**:
   - Week 7-8: Development environments
   - Week 9-10: Staging/Test
   - Week 11-12: Production (with maintenance windows)
2. **Update Moxxie** - Point to new router IPs
3. **Update monitoring** - Ensure all alerts work

### Phase 5: Optimization (Month 4+)
1. **Tune BGP** - Optimize routing policies
2. **Add features**:
   - Traffic engineering
   - QoS policies
   - Advanced firewall rules
3. **Plan switch replacement** - If needed

## Operational Procedures

### Adding a New VLAN

```bash
# 1. Add to both DC routers

# DC1 (standard VLAN)
/interface vlan add name=vlan500-newservice interface=uplink-to-switches vlan-id=500
/ip address add address=172.16.50.1/24 interface=vlan500-newservice

# DC2 (Q-in-Q VLAN)
/interface vlan add name=vlan500-newservice interface=svlan1000 vlan-id=500
/ip address add address=172.20.50.1/24 interface=vlan500-newservice

# 2. Update firewall rules if needed
/ip firewall filter add chain=forward in-interface=vlan500-newservice ...

# 3. Update Moxxie configuration
```

### Monitoring Health

Key metrics to watch:
```bash
# BGP session status (should be "Established")
/routing bgp session print

# Interface status
/interface print where running=no

# Q-in-Q packet counts (DC2)
/interface vlan print stats where name~"svlan"

# Route count
/ip route print count-only where bgp
```

### Failover Testing

Monthly test procedure:
1. Announce maintenance window
2. Disable BGP session: `/routing bgp connection disable to-dc2`
3. Verify traffic reroutes
4. Re-enable after 5 minutes
5. Document any issues

## Troubleshooting

### Common Issues

#### Q-in-Q Frames Not Passing
```bash
# Check MTU (needs to be 1504+ for Q-in-Q)
/interface ethernet print where name=uplink-to-switches

# Verify service tag
/interface vlan print where use-service-tag=yes

# Check switch CPU for dropped packets
/system resource monitor
```

#### BGP Session Won't Establish
```bash
# Check connectivity
/ping 10.255.255.2

# Verify BGP config
/routing bgp connection print detail

# Check firewall
/ip firewall filter print where chain=input
```

#### Asymmetric Routing Issues
```bash
# Ensure consistent preferences
/routing filter rule print

# Check both directions
/ip route print where gateway=10.255.255.2
```

### Emergency Procedures

#### Complete DC Router Failure
1. **Immediate**: Traffic continues on other DC
2. **Within 1 hour**: Deploy cold spare router
3. **Recovery**: Restore config from backup

#### Provider S-VLAN Change
1. **Update service VLAN**: Change vlan-id on svlan interface
2. **No other changes needed**: Customer VLANs unchanged

## Future Enhancements

### When You Get Your Own Switches in DC2

The beauty of this design is the easy migration:

```bash
# Simply remove the service VLAN layer
# Change from:
/interface vlan
add name=vlan100-prod interface=svlan1000 vlan-id=100

# To:
/interface vlan
add name=vlan100-prod interface=uplink-to-switches vlan-id=100

# Everything else stays the same!
```

### Integration with Cilium BGP

Once stable, add Kubernetes integration:
```yaml
# Cilium can peer with your routers
apiVersion: v1
kind: ConfigMap
metadata:
  name: bgp-config
data:
  peers:
    - peer-address: 172.16.300.1  # DC1 router
      peer-asn: 65001
    - peer-address: 172.20.300.1  # DC2 router  
      peer-asn: 65001
```

### Adding More DCs

The architecture scales linearly:
- DC3: AS65001, 172.24.0.0/16
- DC4: AS65001, 172.28.0.0/16
- Full mesh iBGP between all routers

## Cost Analysis

### Initial Investment
- 2x CCR2004 routers: $790
- 2x 10G transceivers: $100  
- Inter-DC dark fiber/wave: Varies
- **Total Hardware**: ~$890

### Ongoing Costs
- Power: ~100W per router
- Cross-connect fees: Varies by DC
- Inter-DC bandwidth: Varies

### ROI Justification
- Avoid managed switch fees
- Enable true multi-DC architecture
- Foundation for all future growth

## Summary

This architecture gives you:
1. **Full VLAN control** in both DCs despite managed switches
2. **Consistent networking** across sites
3. **BGP routing** for redundancy and growth
4. **Easy migration path** when you get your own switches
5. **Integration ready** for Cilium and other tools

The Q-in-Q + BGP approach turns your constraint (managed switches) into an advantage (forced good architecture). Start with the routers, prove the concept, then scale as needed.

## Next Steps

1. Review this plan
2. Get budget approval for routers (~$890)
3. Schedule lab time for testing
4. Coordinate with DC2 provider on S-VLAN details
5. Plan maintenance windows for migration

Remember: This is a marathon, not a sprint. Take it phase by phase, test thoroughly, and you'll end up with a rock-solid network architecture.