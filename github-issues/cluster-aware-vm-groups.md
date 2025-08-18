# Cluster-Aware VM Group Management

## Overview
Implement VM group management capabilities that allow coordinated lifecycle operations on related VMs. This enables treating multiple VMs as a single logical unit for operations like start, stop, delete, and migrate.

## Background
Modern applications and Kubernetes clusters consist of multiple VMs that need coordinated management:
- **Kubernetes clusters**: Bootstrap, masters, workers need ordered startup
- **Application stacks**: Database, app servers, load balancers
- **Dev environments**: Complete environments that start/stop together
- **Disaster recovery**: Coordinated failover of VM groups

## Requirements

### Core Features
1. **VM Group Definition**: Create and manage logical VM groups
2. **Lifecycle Operations**: Start, stop, restart, delete groups
3. **Ordering Control**: Define startup/shutdown sequences
4. **Dependency Management**: Express dependencies between VMs
5. **State Tracking**: Monitor group and member states

### Advanced Features
1. **Role-Based Operations**: Different operations for different roles
2. **Temporary Members**: Auto-remove after certain conditions
3. **Health Checks**: Verify group health before operations
4. **Rollback Support**: Undo failed group operations
5. **Event Notifications**: Webhooks for group state changes

## Implementation

### Data Models
```java
public record VMGroup(
    String groupId,
    String name,
    GroupType type,           // CLUSTER, STACK, ENVIRONMENT
    List<VMGroupMember> members,
    Map<String, String> metadata,
    GroupLifecycle lifecycle
) {}

public record VMGroupMember(
    int vmId,
    String role,              // bootstrap, master, worker, etc.
    int startOrder,           // 1, 2, 3...
    int stopOrder,            // reverse of start usually
    boolean temporary,        // auto-remove after use
    Duration startDelay,      // delay after starting
    List<String> dependsOn    // other member roles
) {}

public record GroupLifecycle(
    StartStrategy startStrategy,  // PARALLEL, SEQUENTIAL, ORDERED
    StopStrategy stopStrategy,    // GRACEFUL, IMMEDIATE, ORDERED
    boolean autoCleanup,          // remove group when empty
    Duration healthCheckTimeout
) {}
```

### API Examples

#### Create Kubernetes Cluster Group
```bash
curl -X POST http://localhost:8080/api/v1/groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "okd-production",
    "type": "CLUSTER",
    "metadata": {
      "platform": "OKD",
      "version": "4.19"
    },
    "members": [
      {
        "vmId": 10710,
        "role": "bootstrap",
        "startOrder": 1,
        "temporary": true,
        "startDelay": "30s"
      },
      {
        "vmId": 10711,
        "role": "master",
        "startOrder": 2,
        "dependsOn": ["bootstrap"],
        "startDelay": "10s"
      },
      {
        "vmId": 10712,
        "role": "master",
        "startOrder": 2,
        "startDelay": "10s"
      },
      {
        "vmId": 10713,
        "role": "master",
        "startOrder": 2,
        "startDelay": "10s"
      }
    ],
    "lifecycle": {
      "startStrategy": "ORDERED",
      "stopStrategy": "GRACEFUL",
      "healthCheckTimeout": "5m"
    }
  }'
```

#### Start Entire Group
```bash
curl -X POST http://localhost:8080/api/v1/groups/okd-production/start

# Response shows progress:
{
  "groupId": "okd-production",
  "operation": "START",
  "status": "IN_PROGRESS",
  "progress": {
    "10710": "STARTED",    # bootstrap started first
    "10711": "STARTING",   # masters starting
    "10712": "STARTING",
    "10713": "PENDING"     # waiting for dependencies
  }
}
```

#### Remove Temporary Members
```bash
curl -X POST http://localhost:8080/api/v1/groups/okd-production/cleanup

# Removes bootstrap node marked as temporary
```

#### Scale Worker Group
```bash
curl -X POST http://localhost:8080/api/v1/groups/okd-production/members \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD",
    "members": [
      {
        "vmId": 10721,
        "role": "worker",
        "startOrder": 3
      },
      {
        "vmId": 10722,
        "role": "worker",
        "startOrder": 3
      }
    ]
  }'
```

#### Delete Entire Group
```bash
curl -X DELETE http://localhost:8080/api/v1/groups/okd-production?deleteVMs=true
```

## Use Cases

### Kubernetes Cluster Management
```bash
# Start cluster in order: bootstrap → masters → workers
curl -X POST /api/v1/groups/k8s-prod/start

# Stop for maintenance (graceful shutdown)
curl -X POST /api/v1/groups/k8s-prod/stop

# Remove bootstrap after cluster formation
curl -X POST /api/v1/groups/k8s-prod/cleanup
```

### Development Environment
```bash
# Start entire dev stack
curl -X POST /api/v1/groups/dev-env-42/start

# Stop at end of day
curl -X POST /api/v1/groups/dev-env-42/stop

# Delete when project ends
curl -X DELETE /api/v1/groups/dev-env-42?deleteVMs=true
```

## Benefits
1. **Simplified Management**: One command for complex operations
2. **Consistency**: Ensure proper startup/shutdown sequences
3. **Automation**: Reduce manual intervention
4. **Safety**: Dependency management prevents issues
5. **Visibility**: Track group state and health

## Success Criteria
1. ✅ Groups can be created, modified, and deleted
2. ✅ Lifecycle operations respect ordering and dependencies
3. ✅ Temporary members are auto-removed correctly
4. ✅ Group state is accurately tracked
5. ✅ Failed operations can be rolled back
6. ✅ API is intuitive and well-documented

## Related Issues
- #36: Cluster Aware Scheduling
- #46: Enhanced Tagging System
- Advanced Cluster Provisioning (local)