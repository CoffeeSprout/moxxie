# Node Maintenance and Drain Workflows

This guide covers Moxxie's node maintenance features for safely migrating VMs during hypervisor maintenance or hardware failures.

## Table of Contents

- [Overview](#overview)
- [Drain Modes](#drain-modes)
- [VM Tag Behavior](#vm-tag-behavior)
- [API Endpoints](#api-endpoints)
- [Usage Examples](#usage-examples)
- [Workflow Patterns](#workflow-patterns)
- [Troubleshooting](#troubleshooting)

## Overview

Moxxie provides automated node drain and maintenance workflows to safely migrate VMs off hypervisors for:
- **Routine maintenance** - Kernel updates, configuration changes, scheduled reboots
- **Hardware failures** - Evacuating VMs from faulty machines
- **Planned downtime** - System upgrades requiring extended maintenance

Key features:
- ✅ **Two drain modes** - Soft (maintenance) and Hard (evacuation)
- ✅ **Tag-aware migration** - Respects `always-on` and `maint-ok` tags
- ✅ **State verification** - Ensures `always-on` VMs remain running
- ✅ **Auto-restart** - Automatically starts VMs that end up stopped
- ✅ **Progress tracking** - Real-time status of each VM migration
- ✅ **Async operations** - Non-blocking API with status polling
- ✅ **Parallel migrations** - Configurable concurrency for faster drains
- ✅ **Undrain support** - Migrate VMs back after maintenance

## Drain Modes

### Soft Drain (Maintenance/Reboot)

**Use for:** Routine maintenance, kernel updates, scheduled reboots

**Behavior:**
- Skips VMs tagged with `maint-ok` (they can handle downtime)
- Live migrates VMs tagged with `always-on` (they must stay up)
- Regular VMs are live migrated
- Faster than hard drain (fewer VMs to migrate)

**When to use:**
- Monthly security patching
- Kernel updates requiring reboot
- Configuration changes
- Brief maintenance windows

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/nodes/hv7/drain \
  -H "Content-Type: application/json" \
  -d '{
    "drainMode": "soft",
    "parallel": true,
    "maxConcurrent": 3
  }'
```

### Hard Drain (Faulty Machine)

**Use for:** Hardware failures, complete machine evacuation

**Behavior:**
- Migrates **ALL** VMs regardless of tags
- Allows offline migration for `maint-ok` VMs (faster)
- Forces live migration for `always-on` VMs
- Clears the node completely

**When to use:**
- Hardware failure detected
- Machine must be taken offline immediately
- Disk/RAID failures
- Permanent decommissioning

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/nodes/hv7/drain \
  -H "Content-Type: application/json" \
  -d '{
    "drainMode": "hard",
    "parallel": true,
    "maxConcurrent": 5
  }'
```

## VM Tag Behavior

Moxxie's drain logic respects VM tags to optimize migration strategies:

### `always-on` Tag

VMs that **MUST** remain running at all times.

**Behavior:**
- **Always live migrated** (never offline)
- **State verified** after migration
- **Auto-restarted** if found stopped
- **Included in both soft and hard drains**

**Use cases:**
- Production web servers
- Critical databases
- Customer-facing applications
- High-SLA workloads

**Example tagging:**
```bash
# Tag a critical VM as always-on
curl -X PUT http://localhost:8080/api/v1/vms/8200/config \
  -H "Content-Type: application/json" \
  -d '{"tags": "always-on,env-prod,client-acme"}'
```

### `maint-ok` Tag

VMs that can tolerate brief downtime for maintenance.

**Behavior in soft drain:**
- **Skipped entirely** (stays on node during reboot)
- Comes back up automatically after maintenance
- Not migrated unless hard drain

**Behavior in hard drain:**
- **Migrated with offline migration** (faster)
- No live migration overhead
- Faster evacuation

**Use cases:**
- Development/staging environments
- Batch processing workers
- Non-critical services
- Background jobs

**Example tagging:**
```bash
# Tag a dev VM as maint-ok
curl -X PUT http://localhost:8080/api/v1/vms/8300/config \
  -H "Content-Type: application/json" \
  -d '{"tags": "maint-ok,env-dev,client-acme"}'
```

### Tag Priority

When VMs have multiple tags:

| Tags | Soft Drain | Hard Drain | Migration Type |
|------|-----------|-----------|----------------|
| `always-on` only | Live migrate | Live migrate | Online |
| `maint-ok` only | **Skip** | Offline migrate | Offline OK |
| `always-on` + `maint-ok` | Live migrate | Live migrate | Online (always-on wins) |
| No tags | Live migrate | Live migrate | Online (default) |

## API Endpoints

### Drain Node

Migrate all VMs off a node.

**Endpoint:** `POST /api/v1/nodes/{node}/drain`

**Request:**
```json
{
  "drainMode": "soft",      // "soft" or "hard"
  "parallel": true,         // Parallel migrations
  "maxConcurrent": 3,       // Max concurrent migrations
  "allowOffline": false,    // Allow offline migration (auto for hard drain)
  "targetNode": null        // Specific target or auto-select
}
```

**Response (202 Accepted):**
```json
{
  "drainId": "uuid",
  "node": "hv7",
  "operation": "drain",
  "status": "in_progress",
  "totalVMs": 15,
  "completedVMs": 0,
  "failedVMs": 0,
  "vmStatus": [],
  "startedAt": "2025-01-15T10:00:00",
  "completedAt": null,
  "message": "Draining node hv7 (soft mode)"
}
```

### Check Drain Status

Get real-time drain progress.

**Endpoint:** `GET /api/v1/nodes/{node}/drain/{drainId}/status`

**Response:**
```json
{
  "drainId": "uuid",
  "node": "hv7",
  "operation": "drain",
  "status": "in_progress",
  "totalVMs": 15,
  "completedVMs": 8,
  "failedVMs": 1,
  "vmStatus": [
    {
      "vmid": 8200,
      "name": "web-prod-01",
      "status": "completed",
      "targetNode": "hv8",
      "error": null
    },
    {
      "vmid": 8201,
      "name": "db-prod-01",
      "status": "in_progress",
      "targetNode": "hv9",
      "error": null
    }
  ],
  "startedAt": "2025-01-15T10:00:00",
  "completedAt": null,
  "message": "Progress: 8/15 completed"
}
```

### Enable Maintenance Mode

Mark a node as in maintenance (prevents new VM placement).

**Endpoint:** `POST /api/v1/nodes/{node}/maintenance?drain=true&reason=Security%20updates`

**Response:**
```json
{
  "node": "hv7",
  "inMaintenance": true,
  "reason": "Security updates",
  "maintenanceStarted": "2025-01-15T10:00:00",
  "maintenanceEnded": null,
  "vmsOnNode": 3,
  "lastDrainId": "uuid",
  "drainStatus": "in_progress"
}
```

### Disable Maintenance Mode

Remove maintenance mode from a node.

**Endpoint:** `DELETE /api/v1/nodes/{node}/maintenance?undrain=false`

### Undrain Node

Migrate VMs back to a node after maintenance.

**Endpoint:** `POST /api/v1/nodes/{node}/undrain`

**Response:** Same format as drain response

## Usage Examples

### Example 1: Monthly Security Patching

```bash
#!/bin/bash
# Patch all hypervisors with rolling maintenance

NODES="hv7 hv8 hv9 hv10"

for node in $NODES; do
  echo "=== Patching $node ==="

  # 1. Soft drain (skips maint-ok VMs)
  drain_response=$(curl -s -X POST http://localhost:8080/api/v1/nodes/$node/drain \
    -H "Content-Type: application/json" \
    -d '{"drainMode": "soft"}')

  drain_id=$(echo $drain_response | jq -r '.drainId')
  echo "Drain started: $drain_id"

  # 2. Wait for drain to complete
  while true; do
    status=$(curl -s http://localhost:8080/api/v1/nodes/$node/drain/$drain_id/status | jq -r '.status')
    completed=$(curl -s http://localhost:8080/api/v1/nodes/$node/drain/$drain_id/status | jq -r '.completedVMs')
    total=$(curl -s http://localhost:8080/api/v1/nodes/$node/drain/$drain_id/status | jq -r '.totalVMs')

    echo "Progress: $completed/$total ($status)"

    if [[ "$status" == "completed" ]]; then
      break
    fi
    sleep 10
  done

  # 3. Enable maintenance mode
  curl -X POST "http://localhost:8080/api/v1/nodes/$node/maintenance?reason=Security%20patching"

  # 4. Apply patches and reboot (via SSH)
  ssh $node "apt update && apt upgrade -y && reboot"
  echo "Rebooting $node..."

  # 5. Wait for node to come back online
  sleep 300
  until ssh $node "uptime" &>/dev/null; do
    echo "Waiting for $node to come back online..."
    sleep 10
  done

  # 6. Disable maintenance mode
  curl -X DELETE http://localhost:8080/api/v1/nodes/$node/maintenance

  echo "$node patched successfully!"
  echo ""
done

echo "All nodes patched!"
```

### Example 2: Hardware Failure Recovery

```bash
#!/bin/bash
# Emergency evacuation of failing node

FAULTY_NODE="hv7"

echo "=== EMERGENCY: Evacuating $FAULTY_NODE ==="

# 1. Hard drain - evacuate ALL VMs immediately
drain_response=$(curl -s -X POST http://localhost:8080/api/v1/nodes/$FAULTY_NODE/drain \
  -H "Content-Type: application/json" \
  -d '{
    "drainMode": "hard",
    "parallel": true,
    "maxConcurrent": 5
  }')

drain_id=$(echo $drain_response | jq -r '.drainId')
echo "Emergency drain started: $drain_id"

# 2. Monitor progress
while true; do
  status_response=$(curl -s http://localhost:8080/api/v1/nodes/$FAULTY_NODE/drain/$drain_id/status)
  status=$(echo $status_response | jq -r '.status')
  completed=$(echo $status_response | jq -r '.completedVMs')
  failed=$(echo $status_response | jq -r '.failedVMs')
  total=$(echo $status_response | jq -r '.totalVMs')

  echo "Progress: $completed/$total completed, $failed failed ($status)"

  # Show individual VM status
  echo $status_response | jq -r '.vmStatus[] | "\(.vmid): \(.status) -> \(.targetNode)"'

  if [[ "$status" == "completed" || "$status" == "failed" ]]; then
    break
  fi
  sleep 5
done

# 3. Mark as maintenance
curl -X POST "http://localhost:8080/api/v1/nodes/$FAULTY_NODE/maintenance?reason=Hardware%20failure"

# 4. Verify all critical VMs are running
echo "Verifying always-on VMs..."
curl -s "http://localhost:8080/api/v1/vms?tags=always-on" | \
  jq -r '.[] | select(.node != "'$FAULTY_NODE'") | "\(.vmid): \(.name) on \(.node) - \(.status)"'

echo "Evacuation complete. Node $FAULTY_NODE is now empty."
```

### Example 3: Planned Maintenance Window

```bash
#!/bin/bash
# Maintenance window with undrain

NODE="hv7"

echo "=== Maintenance Window for $NODE ==="

# 1. Soft drain with maintenance mode
curl -X POST "http://localhost:8080/api/v1/nodes/$NODE/maintenance?drain=true&reason=Planned%20maintenance"

# 2. Wait for drain
# (drain is triggered automatically by maintenance mode with drain=true)

# 3. Perform maintenance
echo "Performing maintenance on $NODE..."
sleep 60  # Your maintenance tasks here

# 4. Disable maintenance and undrain
curl -X DELETE "http://localhost:8080/api/v1/nodes/$NODE/maintenance?undrain=true"

echo "Maintenance complete. VMs migrating back to $NODE."
```

### Example 4: Check Always-On VM State

```bash
#!/bin/bash
# Verify all always-on VMs are running correctly

echo "=== Checking Always-On VMs ==="

curl -s "http://localhost:8080/api/v1/vms?tags=always-on" | jq -r '
  .[] |
  if .status == "running" then
    "\u2705 \(.vmid): \(.name) on \(.node) - RUNNING"
  else
    "\u274c \(.vmid): \(.name) on \(.node) - \(.status) ⚠️  ALERT!"
  end
'
```

## Workflow Patterns

### Pattern 1: Rolling Updates

For updating multiple nodes without service interruption:

```
For each node:
  1. Soft drain → 2. Update → 3. Reboot → 4. Disable maintenance
  (maint-ok VMs stay, always-on VMs migrate)
```

**Advantages:**
- Minimal migrations
- Faster updates
- maint-ok VMs reboot with node
- always-on VMs stay running

### Pattern 2: Emergency Evacuation

For hardware failures:

```
1. Hard drain (all VMs) → 2. Enable maintenance → 3. Replace hardware
```

**Advantages:**
- Complete evacuation
- Fast offline migration for maint-ok VMs
- Verified state for always-on VMs

### Pattern 3: Maintenance with Fallback

For risky maintenance with rollback option:

```
1. Soft drain → 2. Maintenance mode → 3. Test → 4. Undrain or keep drained
```

**Advantages:**
- Can rollback by undraining
- VMs preserved in original location
- Easy recovery

## Troubleshooting

### Issue: Drain Stuck in Progress

**Symptoms:** Drain operation not completing

**Causes:**
- VM migration failing
- Target node unavailable
- Network issues

**Resolution:**
```bash
# Check drain status
curl http://localhost:8080/api/v1/nodes/hv7/drain/{drainId}/status | jq .

# Look for failed VMs
curl http://localhost:8080/api/v1/nodes/hv7/drain/{drainId}/status | \
  jq '.vmStatus[] | select(.status == "failed")'

# Check migration logs
tail -f quarkus-dev.log | grep "Migration"
```

### Issue: Always-On VM Found Stopped

**Symptoms:** Warning in logs about always-on VM being stopped

**Automatic recovery:**
```
[WARN] Always-on VM 8200 (web-prod-01) is stopped after migration, starting it
[INFO] Successfully started always-on VM 8200
```

Moxxie automatically detects and restarts stopped always-on VMs.

**Manual verification:**
```bash
# Check if VM is running now
curl http://localhost:8080/api/v1/vms/8200 | jq '.status'

# Manually start if needed
curl -X POST http://localhost:8080/api/v1/vms/8200/power/start
```

### Issue: Cannot Select Target Node

**Symptoms:** Migration fails with "No suitable target node found"

**Causes:**
- All other nodes in maintenance
- Insufficient resources on target nodes
- All nodes are the same as source

**Resolution:**
```bash
# Check which nodes are in maintenance
curl http://localhost:8080/api/v1/nodes | jq '.[] | select(.status == "maintenance")'

# Check available nodes
curl http://localhost:8080/api/v1/nodes | jq '.[] | select(.status != "maintenance") | .node'

# Disable maintenance on a node if needed
curl -X DELETE http://localhost:8080/api/v1/nodes/hv8/maintenance
```

### Issue: Maint-OK VMs Not Being Skipped

**Symptoms:** Soft drain migrating maint-ok VMs

**Check:**
```bash
# Verify VM has maint-ok tag
curl http://localhost:8080/api/v1/vms/8300 | jq '.tags'

# Should contain "maint-ok"
# If not, add it:
curl -X PUT http://localhost:8080/api/v1/vms/8300/config \
  -H "Content-Type: application/json" \
  -d '{"tags": "maint-ok,env-dev"}'
```

### Logs

Enable detailed logging for troubleshooting:

```properties
# In application.properties
quarkus.log.category."com.coffeesprout.service.NodeMaintenanceService".level=DEBUG
quarkus.log.category."com.coffeesprout.service.MigrationService".level=DEBUG
```

**Useful log patterns:**
```bash
# Find drain operations
grep "Draining node" quarkus-dev.log

# Find always-on VM issues
grep "always-on" quarkus-dev.log | grep -i "warn\|error"

# Find migration failures
grep "Failed to migrate" quarkus-dev.log

# Find state corrections
grep "starting it" quarkus-dev.log
```

## Database Schema

Node maintenance state is persisted in the `node_maintenance` table:

```sql
-- Check current maintenance status
SELECT node_name, in_maintenance, reason, maintenance_started
FROM node_maintenance
WHERE in_maintenance = true;

-- Check drain history
SELECT node_name, drain_status, last_drain_id, updated_at
FROM node_maintenance
ORDER BY updated_at DESC
LIMIT 10;

-- Check which VMs were on a node before drain
SELECT node_name, vm_list
FROM node_maintenance
WHERE node_name = 'hv7'
ORDER BY updated_at DESC
LIMIT 1;
```

## Related Documentation

- [API Examples](./API_EXAMPLES.md) - VM management examples
- [CLAUDE.md](./CLAUDE.md) - Complete project documentation
- [Migration Documentation](./CLAUDE.md#vm-migration-support) - VM migration details
- [Tagging System](./CLAUDE.md#tagging-system) - VM tag documentation

## Support

For issues or questions:
1. Check logs: `tail -f quarkus-dev.log | grep "NodeMaintenance"`
2. Verify drain status via API
3. Check VM tags are correct
4. Ensure target nodes are available
