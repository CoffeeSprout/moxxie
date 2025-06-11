# Phase 1: Foundation - CLI to REST Transformation

## Overview
Transform the existing Picocli-based CLI application into a REST API service using Quarkus with virtual threads. This phase focuses on restructuring without adding new features.

## Objectives
- Remove CLI dependencies (Picocli)
- Set up REST endpoints structure
- Enable virtual threads for better concurrency
- Maintain existing ProxmoxClient functionality
- Simplify configuration for single-cluster operation

## Technical Requirements

### Virtual Threads Configuration
```properties
# application.properties
quarkus.native.additional-build-args=--enable-preview
quarkus.vertx.prefer-native-transport=true
quarkus.rest.virtual-threads=true
```

### Dependencies to Add
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-openapi</artifactId>
</dependency>
```

### Dependencies to Remove
```xml
<!-- Remove these -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-picocli</artifactId>
</dependency>
```

## Implementation Steps

### 1. Project Structure Refactoring
```
src/main/java/com/coffeesprout/
├── api/                    # NEW: REST endpoints
│   ├── InfoResource.java
│   └── HealthResource.java
├── service/                # REFACTOR: Extract business logic
│   └── ClusterService.java
├── client/                 # KEEP: Proxmox client
└── model/                  # KEEP: Data models
```

### 2. Remove CLI Classes
- Delete: MainCLI.java, DiscoverCommand.java, ListCommand.java, ProvisionCommand.java
- Extract useful logic into service classes

### 3. Create Base REST Resources
```java
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiResource {
    // Base configuration
}
```

### 4. Simplify Configuration
```properties
# Single cluster configuration
moxxie.instance.id=wsdc1
moxxie.instance.location=Amsterdam
moxxie.proxmox.url=https://10.0.0.10:8006/api2/json
moxxie.proxmox.username=root@pam
```

## Testing Requirements
- Unit tests for service extraction
- Integration tests for REST endpoints
- Verify virtual threads are working
- Test with existing Proxmox setup

## Deliverables
1. REST API service skeleton running on port 8080
2. Health check endpoint at `/q/health`
3. OpenAPI documentation at `/q/openapi`
4. Service classes with extracted business logic
5. Simplified single-cluster configuration

## Success Criteria
- Application starts as REST service (not CLI)
- Virtual threads enabled and verified
- Existing ProxmoxClient still works
- Clean separation of API/Service/Client layers