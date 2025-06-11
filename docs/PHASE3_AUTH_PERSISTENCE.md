# Phase 3: Authentication & Persistence

## Overview
Add Keycloak OIDC authentication and PostgreSQL persistence for managing state and audit trails.

## Keycloak Integration

### Dependencies
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-security</artifactId>
</dependency>
```

### Configuration
```properties
# Keycloak OIDC Configuration
quarkus.oidc.auth-server-url=https://auth.example.com/realms/admin
quarkus.oidc.client-id=moxxie-wsdc1
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
quarkus.oidc.application-type=service
quarkus.oidc.roles.source=accesstoken

# API Security
quarkus.http.auth.permission.api.paths=/api/*
quarkus.http.auth.permission.api.policy=authenticated
quarkus.http.auth.permission.health.paths=/q/health/*
quarkus.http.auth.permission.health.policy=permit
```

### Security Implementation
```java
@Path("/api/v1/vms")
@RolesAllowed("infrastructure-admin")
public class VMResource {
    @Inject
    @AuthzClient
    SecurityIdentity identity;
    
    @POST
    public VMResponse createVM(CreateVMRequest request) {
        var user = identity.getPrincipal().getName();
        // Audit log the action
        auditService.log(user, "CREATE_VM", request);
        return vmService.createVM(request);
    }
}
```

## PostgreSQL Persistence

### Dependencies
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm-panache</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
```

### Database Schema
```sql
-- V1__initial_schema.sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vm_state (
    vm_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    node VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    metadata JSONB
);

CREATE TABLE resource_locks (
    resource_id VARCHAR(255) PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    lock_reason VARCHAR(500)
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_vm_state_node ON vm_state(node);
```

### Entity Models
```java
@Entity
@Table(name = "audit_logs")
public class AuditLog extends PanacheEntityBase {
    @Id
    @GeneratedValue
    public UUID id;
    
    @Column(name = "user_id")
    public String userId;
    
    public String action;
    
    @Column(name = "resource_type")
    public String resourceType;
    
    @Column(name = "resource_id")
    public String resourceId;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> details;
    
    public Instant timestamp;
}
```

### Service Integration
```java
@ApplicationScoped
@Transactional
public class VMService {
    @Inject
    AuditService auditService;
    
    public VMResponse createVM(CreateVMRequest request) {
        // Create in Proxmox
        var vm = proxmoxService.createVM(request);
        
        // Store state
        var vmState = new VMState();
        vmState.vmId = vm.getId();
        vmState.name = vm.getName();
        vmState.createdBy = identity.getPrincipal().getName();
        vmState.persist();
        
        // Audit
        auditService.logVMCreation(vm);
        
        return vm;
    }
}
```

## Testing Requirements
- Test Keycloak integration with test realm
- Database migration tests
- Transaction rollback scenarios
- Performance testing with connection pool

## Deliverables
1. Keycloak authentication working
2. Database schema and migrations
3. Audit logging for all operations
4. Resource state tracking
5. Transaction management
6. Security tests

## Success Criteria
- All endpoints require authentication
- Actions are audited with user information
- State is persisted and queryable
- Database performs well under load
- Proper error handling for auth failures