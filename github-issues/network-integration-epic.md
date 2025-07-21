# Epic: Network Infrastructure Integration

## Overview
This epic tracks the implementation of network automation features in Moxxie to support the new BGP+RouterOS infrastructure architecture.

## Background
Based on the network architecture planning, we're implementing:
- MikroTik CCR2116 routers in both datacenters
- iBGP between datacenters (no external BGP due to lack of /24)
- Q-in-Q for DC2 to work with managed switches
- Support for Kubernetes clusters with Cilium BGP

## Sub-Issues

### Phase 1: Foundation
- [ ] #XX - Add IPAM (IP Address Management) Functionality
- [ ] #XX - Integrate RouterOS API for Network Automation

### Phase 2: Multi-DC Support  
- [ ] #XX - Implement Multi-DC Architecture Support
- [ ] #XX - Handle DC2 Q-in-Q encapsulation transparently

### Phase 3: Kubernetes Integration
- [ ] #XX - Add Kubernetes Cluster Registry and BGP Support
- [ ] #XX - Generate Cilium BGP configurations

### Phase 4: Orchestration
- [ ] #XX - Implement Complete Network Provisioning Workflow
- [ ] #XX - Add network deprovisioning and cleanup

## Architecture Decisions
- **No external BGP** - We lack /24 for ISP peering
- **CCR2116 routers** - Best price/performance ratio
- **Single router per DC initially** - Add redundancy later via VRRP
- **Each K8s cluster gets a VLAN** - With BGP peering to router

## Implementation Order
1. Start with IPAM to track allocations
2. Add RouterOS API for basic operations
3. Make everything DC-aware
4. Add K8s cluster tracking
5. Tie it all together with provisioning workflow

## Success Criteria
- [ ] Can provision networks automatically
- [ ] IPAM prevents conflicts
- [ ] DC2 Q-in-Q handled transparently
- [ ] K8s clusters get BGP configs
- [ ] Full audit trail of changes

## Testing Strategy
- Use CHR (Cloud Hosted Router) for integration tests
- Mock RouterOS API for unit tests
- Test DC failover scenarios
- Verify Q-in-Q configuration

## Documentation Needs
- [ ] Network provisioning guide
- [ ] RouterOS API setup instructions
- [ ] K8s BGP integration guide
- [ ] Troubleshooting runbook

Labels: `epic`, `networking`
Milestone: Network Integration