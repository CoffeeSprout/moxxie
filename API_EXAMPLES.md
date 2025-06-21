# Moxxie API Examples

This document provides working examples of Moxxie's REST API endpoints. All examples assume Moxxie is running on `http://localhost:8080`.

## Table of Contents
- [VM Management](#vm-management)
- [Snapshot Management](#snapshot-management)
- [Bulk Snapshot Operations](#bulk-snapshot-operations)
- [Tag Management](#tag-management)
- [Scheduler Management](#scheduler-management)
- [Backup Operations](#backup-operations)

## VM Management

### List All VMs
```bash
curl -X GET http://localhost:8080/api/v1/vms | jq .
```

### Filter VMs by Tags
```bash
# Filter by multiple tags (AND logic)
curl "http://localhost:8080/api/v1/vms?tags=client:nixz,env:prod" | jq .

# Filter by client (convenience)
curl "http://localhost:8080/api/v1/vms?client=nixz" | jq .
```

### Get Specific VM Details
```bash
curl -X GET http://localhost:8080/api/v1/vms/8200 | jq .
```

### List VMs in Moxxie Pool (8200-8209)
```bash
curl -X GET http://localhost:8080/api/v1/vms | jq '.[] | select(.vmid >= 8200 and .vmid <= 8209) | {vmid, name, status}'
```

## Snapshot Management

### List Snapshots for a VM
```bash
curl -X GET http://localhost:8080/api/v1/vms/8200/snapshots | jq .
```

### Create Single Snapshot with TTL
```bash
# Create snapshot with 4-hour TTL
curl -X POST http://localhost:8080/api/v1/vms/8200/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-20240621",
    "description": "Before system updates",
    "ttlHours": 4
  }' | jq .
```

### Delete a Snapshot
```bash
curl -X DELETE "http://localhost:8080/api/v1/vms/8200/snapshots/pre-update-20240621" | jq .
```

## Bulk Snapshot Operations

### Create Snapshots by VM IDs
```bash
# Dry run first
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "snapshotName": "bulk-{vm}-{date}",
    "description": "Bulk snapshot test",
    "ttlHours": 24,
    "dryRun": true
  }' | jq .

# Actual creation
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "VM_IDS", "value": "8200,8201,8202"}
    ],
    "snapshotName": "maint-{vm}-{date}",
    "description": "Pre-maintenance snapshot",
    "ttlHours": 24,
    "dryRun": false
  }' | jq .
```

### Create Snapshots by Name Pattern
```bash
# Snapshot all worker nodes
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-wk-*"}
    ],
    "snapshotName": "workers-backup-{date}",
    "description": "Worker nodes backup",
    "ttlHours": 48,
    "maxParallel": 3
  }' | jq .
```

### Create Snapshots by Tag Expression
```bash
# Snapshot all production VMs for a specific client
curl -X POST http://localhost:8080/api/v1/snapshots/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "env:prod AND client:acme"}
    ],
    "snapshotName": "prod-backup-{date}",
    "description": "Production backup",
    "ttlHours": 72
  }' | jq .
```

## Tag Management

### Get All Unique Tags
```bash
curl -X GET http://localhost:8080/api/v1/tags | jq .
```

### Get VMs with Specific Tag
```bash
curl -X GET "http://localhost:8080/api/v1/tags/client-nixz/vms" | jq .
```

### Bulk Tag Operations
```bash
# Add tags to VMs by name pattern
curl -X POST "http://localhost:8080/api/v1/tags/bulk?namePattern=workshop-*" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD", 
    "tags": ["workshop", "env:test"]
  }' | jq .

# Remove tags from specific VMs
curl -X POST "http://localhost:8080/api/v1/tags/bulk?vmIds=8200,8201,8202" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "REMOVE",
    "tags": ["temp"]
  }' | jq .
```

## Scheduler Management

### List All Scheduled Jobs
```bash
curl -X GET http://localhost:8080/api/v1/scheduler/jobs | jq .
```

### Create Scheduled Snapshot Job
```bash
# Daily snapshots at 2 AM with 7-day rotation
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "daily-snapshots",
    "description": "Daily snapshots for workshop VMs",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "description": "Automated daily snapshot",
      "maxSnapshots": "7"
    },
    "vmSelectors": [
      {"type": "NAME_PATTERN", "value": "workshop-*"}
    ]
  }' | jq .
```

### Create Snapshot Cleanup Job
```bash
# Hourly cleanup of expired snapshots
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cleanup-expired-snapshots",
    "description": "Remove snapshots past their TTL",
    "taskType": "snapshot_delete",
    "cronExpression": "0 0 * * * ?",
    "enabled": true,
    "parameters": {
      "checkDescription": "true",
      "safeMode": "true",
      "dryRun": "false"
    },
    "vmSelectors": [
      {"type": "ALL", "value": "*"}
    ]
  }' | jq .
```

### Create Pre-Update Snapshot Job (Manual Trigger)
```bash
# Manual trigger job for pre-update snapshots with 24h TTL
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-snapshots",
    "description": "Create snapshots before system updates",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 0 1 1 ? 2099",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "preupd-{vm}-{datetime}",
      "description": "Pre-update checkpoint",
      "snapshotTTL": "24"
    },
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "maint-ok AND NOT always-on"}
    ]
  }' | jq .
```

### Trigger Job Manually
```bash
# Get job ID first
JOB_ID=$(curl -s http://localhost:8080/api/v1/scheduler/jobs | jq -r '.[] | select(.name == "pre-update-snapshots") | .id')

# Trigger the job
curl -X POST "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID/trigger" | jq .
```

### Update Job
```bash
# Disable a job temporarily
curl -X PUT "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": false
  }' | jq .
```

### Delete Job
```bash
curl -X DELETE "http://localhost:8080/api/v1/scheduler/jobs/$JOB_ID" | jq .
```

## Backup Operations

### List All Backups
```bash
curl -X GET http://localhost:8080/api/v1/backups | jq .
```

### Get Retention Candidates
```bash
# Get backups older than 30 days
curl "http://localhost:8080/api/v1/backups/retention-candidates?retentionPolicy=days:30" | jq .

# Get backups to keep only last 5
curl "http://localhost:8080/api/v1/backups/retention-candidates?retentionPolicy=count:5&tags=env:dev" | jq .
```

### Cleanup Old Backups
```bash
# Dry run first
curl -X POST http://localhost:8080/api/v1/backups/cleanup \
  -H "Content-Type: application/json" \
  -d '{
    "retentionPolicy": "days:30",
    "dryRun": true,
    "tags": ["env:dev"]
  }' | jq .
```

## Common Patterns

### VM Selector Types
- `ALL`: Select all VMs
- `VM_IDS`: Comma-separated VM IDs (e.g., "8200,8201,8202")
- `NAME_PATTERN`: Wildcard patterns (e.g., "web-*", "*-prod", "app-?-server")
- `TAG_EXPRESSION`: Boolean expressions (e.g., "env:prod AND client:acme", "backup:daily OR backup:weekly")

### Cron Expression Examples
- `0 0 2 * * ?` - Every day at 2:00 AM
- `0 0 * * * ?` - Every hour
- `0 0 0 * * MON` - Every Monday at midnight
- `0 0/15 * * * ?` - Every 15 minutes
- `0 0 0 1 1 ? 2099` - Never (manual trigger only)

### Placeholder Patterns
- `{vm}` - VM name
- `{vmid}` - VM ID
- `{date}` - Date in YYYYMMDD format
- `{time}` - Time in HHMMSS format
- `{datetime}` - Combined date and time

### TTL Format
TTL is specified in hours (1-8760). When set, it's appended to descriptions as "(TTL: Xh)" and can be processed by the cleanup scheduler.