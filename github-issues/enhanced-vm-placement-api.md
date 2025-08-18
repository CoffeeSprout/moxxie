# Enhanced VM Placement API

## Overview
Extend Moxxie's VM placement capabilities with sophisticated constraints, affinity rules, and resource-aware scheduling. This enhancement provides fine-grained control over where VMs are placed within the Proxmox cluster.

## Background
Current VM placement is basic - either random selection or manual node specification. Production deployments need:
- **Anti-affinity**: Spread similar VMs across different nodes (HA)
- **Affinity**: Keep related VMs together (performance)
- **Resource constraints**: Place based on available resources
- **Hardware requirements**: GPUs, NVMe, specific CPU types
- **Maintenance awareness**: Avoid nodes scheduled for maintenance

## Requirements

### Placement Strategies
1. **Anti-Affinity Rules**: Spread VMs across nodes
2. **Affinity Rules**: Co-locate VMs on same node
3. **Resource-Based**: Place based on CPU/RAM/storage availability
4. **Hardware Constraints**: Specific hardware requirements
5. **Zone/Rack Awareness**: Physical topology considerations

### Constraint Types
1. **Hard Constraints**: Must be satisfied (fail if not possible)
2. **Soft Constraints**: Best effort (warn if not satisfied)
3. **Weighted Preferences**: Score-based placement
4. **Exclusions**: Never place on specific nodes

## Implementation

### Data Models
```java
public record PlacementSpec(
    PlacementStrategy strategy,
    List<PlacementConstraint> constraints,
    List<NodeSelector> nodeSelectors,
    ResourceRequirements resources,
    Map<String, String> labels
) {}

public record PlacementConstraint(
    ConstraintType type,        // ANTI_AFFINITY, AFFINITY, EXCLUDE
    ConstraintLevel level,       // HARD, SOFT, PREFER
    String scope,                // NODE, RACK, ZONE
    String selector,             // tag, group, or VM pattern
    Integer weight               // for PREFER level
) {}

public record NodeSelector(
    SelectorType type,           // TAG, NAME, HARDWARE, RESOURCE
    String key,
    String operator,             // IN, NOT_IN, EXISTS, GT, LT
    List<String> values
) {}

public record PlacementResult(
    int selectedNode,
    String nodeName,
    PlacementScore score,
    List<String> warnings,
    Map<String, Object> reasoning
) {}
```

### API Examples

#### Anti-Affinity for HA Cluster
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "k8s-master-1",
    "placement": {
      "strategy": "ANTI_AFFINITY",
      "constraints": [{
        "type": "ANTI_AFFINITY",
        "level": "HARD",
        "scope": "NODE",
        "selector": "name:k8s-master-*"
      }],
      "nodeSelectors": [{
        "type": "TAG",
        "key": "ssd",
        "operator": "EXISTS"
      }]
    }
  }'

# Places on different node than other k8s-master-* VMs
```

#### Resource-Aware Placement
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "large-db",
    "memoryMB": 65536,
    "placement": {
      "strategy": "RESOURCE_OPTIMIZED",
      "resources": {
        "minAvailableMemory": 80000,
        "minAvailableCores": 16,
        "preferNVMe": true
      },
      "constraints": [{
        "type": "EXCLUDE",
        "level": "HARD",
        "selector": "tag:maintenance"
      }]
    }
  }'
```

#### Affinity for Performance
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "app-cache",
    "placement": {
      "strategy": "AFFINITY",
      "constraints": [{
        "type": "AFFINITY",
        "level": "SOFT",
        "scope": "NODE",
        "selector": "vmid:301",  # Place near database VM
        "weight": 100
      }]
    }
  }'
```

#### Multi-Constraint Placement
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gpu-workload",
    "placement": {
      "strategy": "WEIGHTED",
      "constraints": [
        {
          "type": "ANTI_AFFINITY",
          "level": "SOFT",
          "selector": "group:gpu-workloads",
          "weight": 50
        },
        {
          "type": "EXCLUDE",
          "level": "HARD",
          "selector": "node:hv1"  # Old hardware
        }
      ],
      "nodeSelectors": [
        {
          "type": "HARDWARE",
          "key": "gpu",
          "operator": "IN",
          "values": ["nvidia-a100", "nvidia-a6000"]
        },
        {
          "type": "RESOURCE",
          "key": "cpu.usage",
          "operator": "LT",
          "values": ["50"]  # Less than 50% CPU usage
        }
      ]
    }
  }'
```

#### Placement Simulation (Dry Run)
```bash
curl -X POST http://localhost:8080/api/v1/placement/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "vmSpec": {
      "cores": 8,
      "memoryMB": 16384
    },
    "placement": {
      "strategy": "ANTI_AFFINITY",
      "selector": "tag:production"
    }
  }'

# Response:
{
  "recommendations": [
    {
      "node": "hv5",
      "score": 95,
      "reasoning": {
        "antiAffinity": "No production VMs on this node",
        "resources": "80% memory available",
        "health": "Node healthy"
      }
    },
    {
      "node": "hv3",
      "score": 82,
      "reasoning": {
        "antiAffinity": "1 production VM present",
        "resources": "65% memory available"
      }
    }
  ]
}
```

## Placement Strategies

### SPREAD (Anti-Affinity)
- Distribute VMs evenly across nodes
- Maximize fault tolerance
- Balance resource usage

### PACK (Consolidation)
- Minimize number of nodes used
- Optimize for power efficiency
- Good for dev/test environments

### RESOURCE_OPTIMIZED
- Place based on best resource fit
- Consider CPU, memory, storage, network
- Prevent resource fragmentation

### WEIGHTED
- Score nodes based on multiple factors
- Combine hard and soft constraints
- Find globally optimal placement

## Benefits
1. **High Availability**: Proper VM distribution
2. **Performance**: Optimal resource utilization
3. **Flexibility**: Complex placement rules
4. **Automation**: Reduce manual placement decisions
5. **Compliance**: Enforce placement policies

## Success Criteria
1. ✅ All placement strategies work correctly
2. ✅ Constraints are properly evaluated
3. ✅ Resource availability is accurately assessed
4. ✅ Placement decisions are logged and explainable
5. ✅ Dry-run mode helps predict placement
6. ✅ Performance impact is minimal

## Related Issues
- #41: Anti-Affinity Rules for VM Distribution
- #39: Intelligent VM Scheduling Engine
- #42: Optional Node Selection in VM Creation API
- #36: Cluster Aware Scheduling