# Phase 4: Cafn8 Integration Features

## Overview
Add specific features required for Cafn8 to orchestrate across multiple Moxxie instances. Focus on resource queries, migration support, and operational endpoints.

## Integration Endpoints

### Resource Queries
```
GET /api/v1/resources/available
Query params: cpu_min, memory_min, network
Response: {
  "candidates": [
    {
      "node": "pve1",
      "availableCpu": 16,
      "availableMemoryGB": 64,
      "networks": ["vlan100", "vlan200"]
    }
  ]
}
```

### Placement Validation
```
POST /api/v1/placement/validate
Request: {
  "requirements": {
    "cpu": 4,
    "memoryGB": 16,
    "diskGB": 100,
    "networks": ["vlan100"]
  }
}
Response: {
  "valid": true,
  "suggestedNode": "pve1",
  "reason": "Best available resources"
}
```

### Migration Support
```
POST /api/v1/migrations/export/{vmId}
Response: {
  "migrationId": "mig-123",
  "vmId": "100",
  "format": "vzdump",
  "sizeBytes": 10737418240,
  "downloadUrl": "/api/v1/migrations/mig-123/download",
  "expiresAt": "2024-01-10T12:00:00Z"
}

POST /api/v1/migrations/import
Request: multipart/form-data with backup file
Response: {
  "vmId": "100",
  "node": "pve1",
  "status": "importing"
}
```

### Bulk Operations
```
POST /api/v1/operations/bulk
Request: {
  "operations": [
    {"type": "CREATE_VM", "params": {...}},
    {"type": "DELETE_VM", "params": {"vmId": "100"}}
  ]
}
Response: {
  "operationId": "op-456",
  "status": "processing",
  "results": []
}

GET /api/v1/operations/{operationId}
```

### Resource Reservations
```
POST /api/v1/reservations
Request: {
  "resources": {
    "cpu": 4,
    "memoryGB": 16
  },
  "duration": "PT5M",
  "reason": "VM creation pending"
}
Response: {
  "reservationId": "res-789",
  "expiresAt": "2024-01-10T10:05:00Z"
}
```

## Implementation Details

### Resource Tracking Service
```java
@ApplicationScoped
public class ResourceTrackingService {
    
    @Scheduled(every = "30s")
    void updateResourceCache() {
        var nodes = proxmoxService.getAllNodes();
        var vms = proxmoxService.getAllVMs();
        
        resourceCache.update(calculateAvailable(nodes, vms));
    }
    
    public List<NodeAvailability> getAvailableNodes(ResourceRequirements req) {
        return resourceCache.getNodes().stream()
            .filter(n -> n.hasCapacity(req))
            .sorted(placementStrategy)
            .collect(toList());
    }
}
```

### Migration Service
```java
@ApplicationScoped
public class MigrationService {
    
    @ConfigProperty(name = "moxxie.migration.storage-path")
    String migrationPath;
    
    public MigrationBundle exportVM(String vmId) {
        var ticket = proxmoxService.createBackup(vmId);
        
        // Wait for backup with virtual thread
        var backup = waitForBackup(ticket);
        
        // Create download URL
        var migrationId = UUID.randomUUID().toString();
        var bundle = new MigrationBundle(
            migrationId,
            vmId,
            backup.getPath(),
            Instant.now().plus(1, HOURS)
        );
        
        migrationCache.put(migrationId, bundle);
        return bundle;
    }
}
```

### Webhook Notifications
```java
@ConfigProperty(name = "moxxie.cafn8.webhook-url")
Optional<String> cafn8WebhookUrl;

@ApplicationScoped
public class CafnEventService {
    
    void notifyVMStateChange(String vmId, String oldState, String newState) {
        if (cafn8WebhookUrl.isPresent()) {
            var event = new StateChangeEvent(
                instanceId,
                vmId,
                oldState,
                newState,
                Instant.now()
            );
            
            webhookClient.post(cafn8WebhookUrl.get(), event);
        }
    }
}
```

## Database Schema Additions
```sql
-- V2__cafn8_integration.sql
CREATE TABLE resource_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cpu INTEGER NOT NULL,
    memory_gb INTEGER NOT NULL,
    reserved_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE migration_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_id VARCHAR(255) NOT NULL,
    direction VARCHAR(20) NOT NULL, -- 'export' or 'import'
    status VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    size_bytes BIGINT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_reservations_expires ON resource_reservations(expires_at);
CREATE INDEX idx_migrations_status ON migration_jobs(status);
```

## Testing Requirements
- Test resource calculations under load
- Migration export/import with large VMs
- Bulk operation transaction handling
- Webhook delivery and retry logic
- Reservation expiry cleanup

## Deliverables
1. Resource query and placement APIs
2. Migration export/import functionality
3. Bulk operation support
4. Resource reservation system
5. Webhook notifications to Cafn8
6. Integration test suite

## Success Criteria
- Cafn8 can query available resources efficiently
- Migrations work reliably between instances
- Bulk operations are transactional
- Webhooks provide real-time updates
- Performance meets Cafn8 requirements