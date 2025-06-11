# Multi-Site Proxmox Architecture Guide

## Overview

This guide explains how to architect and manage Proxmox clusters across multiple sites and providers using Moxxie's federation capabilities. This approach is designed for organizations running infrastructure across different providers like Worldstream, Databarn, and Hetzner.

## Architecture Principles

### Why Separate Clusters?

**Technical Constraints:**
- Corosync requires <10ms RTT (ideally <5ms) for reliable cluster communication
- Cross-provider latency typically ranges from 20-100ms
- Internet connections are too unreliable for cluster heartbeats
- Risk of split-brain scenarios when stretching clusters across providers

**Recommended Approach: Federated Independent Clusters**
- Each provider/location runs its own Proxmox cluster
- Clusters operate independently with their own quorum
- Moxxie provides orchestration layer across clusters
- Cross-cluster operations handled at application level

## Configuration Model

### Cluster Hierarchy

```yaml
clusters:
  - id: unique-identifier
    tier: primary|secondary|edge
    provider: provider-name
    location: geographic-location
```

**Tier Definitions:**
- `primary`: Main production clusters with highest SLA
- `secondary`: DR/backup clusters with good availability
- `edge`: Development/edge clusters with basic availability

### Resource Pools

Resource pools provide logical grouping across physical clusters:

```yaml
resource_pools:
  - name: production-pool
    clusters: [cluster-id-1, cluster-id-2]
    placement_policy:
      strategy: balanced|performance|cost|geographic
```

### Migration Policies

Define how VMs move between clusters:

```yaml
migration_policies:
  paths:
    - source_cluster: worldstream-prod
      target_cluster: databarn-dr
      method: backup-restore  # Most reliable for cross-provider
```

## Real-World Scenarios

### Scenario 1: Multi-Provider Production

**Setup:**
- Primary cluster at Worldstream (premium connectivity)
- DR cluster at Databarn (cost-effective backup)
- Edge cluster at Hetzner (development/testing)

**Configuration Approach:**
1. Define separate clusters with appropriate tiers
2. Create resource pools spanning primary/DR for production
3. Set up async replication between sites
4. Configure migration paths with bandwidth limits

### Scenario 2: Geographic Distribution

**Setup:**
- EU cluster in Amsterdam
- US cluster in New York
- APAC cluster in Singapore

**Configuration Approach:**
1. Use location-based placement policies
2. Configure cross-region backup strategies
3. Implement follow-the-sun operations

## Usage Examples

### 1. Check Federation Status

```bash
./mvnw quarkus:dev -Dquarkus.args='federate status -c multi-site.yaml'
```

### 2. Find Best Cluster for VM

```bash
./mvnw quarkus:dev -Dquarkus.args='federate allocate -c multi-site.yaml \
  -p production-pool --cpu 4 --memory 8GB --location Amsterdam'
```

### 3. Migrate VM Between Clusters

```bash
./mvnw quarkus:dev -Dquarkus.args='federate migrate -c multi-site.yaml \
  vm-100 --from worldstream-prod --to databarn-dr'
```

### 4. Validate Configuration

```bash
./mvnw quarkus:dev -Dquarkus.args='federate validate -c multi-site.yaml'
```

## Migration Strategies

### 1. Backup-Restore Migration (Recommended)

**Pros:**
- Works across any network connection
- No shared storage required
- Can compress and encrypt transfers

**Cons:**
- VM downtime required
- Slower than live migration

**Use When:**
- Moving between providers
- Network latency >10ms
- Different storage backends

### 2. Storage Migration

**Pros:**
- Minimal downtime
- Preserves VM state

**Cons:**
- Requires storage replication setup
- Complex configuration

**Use When:**
- Same provider with storage replication
- Low-latency connections available

### 3. Offline Migration

**Pros:**
- Simple and reliable
- Low risk

**Cons:**
- Extended downtime
- Manual process

**Use When:**
- Non-critical workloads
- Major infrastructure changes

## Best Practices

### 1. Network Architecture

**VPN Connections:**
- Use site-to-site VPNs for management traffic
- Implement redundant VPN endpoints
- Monitor latency and packet loss

**Bandwidth Management:**
- Set migration bandwidth limits
- Schedule large transfers during off-peak
- Use compression for cross-provider transfers

### 2. Storage Strategy

**Within Cluster:**
- Use Ceph for high availability
- Local SSD for performance
- Consider storage tiers

**Cross-Cluster:**
- Async replication for DR
- Regular backup schedules
- Test restore procedures

### 3. Placement Policies

**Production Workloads:**
```yaml
placement_policy:
  strategy: balanced
  rules:
    - type: affinity
      target: cluster
      operator: should
      value: primary-cluster
```

**Development Workloads:**
```yaml
placement_policy:
  strategy: cost
  rules:
    - type: location
      target: cluster
      operator: must_not
      value: production-cluster
```

### 4. Monitoring and Alerting

**Key Metrics:**
- Cross-cluster network latency
- Storage replication lag
- Cluster resource utilization
- Failed migration attempts

**Health Checks:**
- Regular connectivity tests
- Storage compatibility verification
- Resource availability monitoring

## Security Considerations

### 1. Authentication

- Use separate credentials per cluster
- Implement credential rotation
- Store passwords in environment variables

### 2. Network Security

- Encrypt all cross-cluster traffic
- Use firewalls to restrict access
- Implement jump hosts for management

### 3. Data Protection

- Encrypt backups at rest
- Use encrypted transfers
- Implement access controls

## Troubleshooting

### Common Issues

**1. Migration Failures:**
- Check network connectivity
- Verify storage compatibility
- Review resource availability

**2. Placement Issues:**
- Validate resource pool configuration
- Check cluster health status
- Review placement rules

**3. Performance Problems:**
- Monitor cross-cluster latency
- Check bandwidth utilization
- Review storage performance

### Debug Commands

```bash
# Test cluster connectivity
federate validate -c config.yaml --check-connectivity

# Dry-run migration
federate migrate vm-100 --from source --to target --dry-run

# Explain placement decision
federate allocate -p pool --explain
```

## Future Enhancements

### Planned Features

1. **Automated Failover:**
   - Health-based cluster failover
   - Automated DR activation

2. **Cost Optimization:**
   - Cross-provider cost analysis
   - Workload placement optimization

3. **Compliance Features:**
   - Data residency enforcement
   - Audit trail for migrations

4. **Advanced Scheduling:**
   - Time-based placement policies
   - Predictive resource allocation