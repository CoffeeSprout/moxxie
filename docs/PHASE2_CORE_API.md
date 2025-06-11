# Phase 2: Core API Implementation

## Overview
Implement the essential REST endpoints for managing a single Proxmox cluster. Focus on simple, synchronous operations leveraging virtual threads for concurrency.

## API Endpoints to Implement

### Instance Information
```
GET /api/v1/info
Response: {
  "instanceId": "wsdc1",
  "location": "Amsterdam", 
  "provider": "Worldstream",
  "version": "1.0.0",
  "capabilities": ["proxmox", "ceph", "vlans"]
}
```

### Cluster Discovery
```
POST /api/v1/cluster/discover
Response: {
  "nodes": [...],
  "storage": [...],
  "networks": [...]
}
```

### Node Management
```
GET /api/v1/nodes
GET /api/v1/nodes/{nodeId}
GET /api/v1/nodes/{nodeId}/status
```

### VM Management
```
GET /api/v1/vms
GET /api/v1/vms/{vmId}
POST /api/v1/vms
DELETE /api/v1/vms/{vmId}
```

### Network Information
```
GET /api/v1/networks
GET /api/v1/networks/{vlan}
```

## Implementation Details

### Resource Classes Structure
```java
@Path("/api/v1/nodes")
@RunOnVirtualThread
public class NodeResource {
    @Inject
    NodeService nodeService;
    
    @GET
    public List<Node> listNodes() {
        return nodeService.getAllNodes();
    }
}
```

### Service Layer Pattern
```java
@ApplicationScoped
public class NodeService {
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    AuthService authService;
    
    public List<Node> getAllNodes() {
        var ticket = authService.getTicket();
        var response = proxmoxClient.getNodes(ticket);
        return response.getData();
    }
}
```

### Error Handling
```java
@Provider
public class ExceptionMappers {
    @ServerExceptionMapper
    public Response mapProxmoxException(ProxmoxException e) {
        return Response.status(502)
            .entity(new ErrorResponse("Proxmox error", e.getMessage()))
            .build();
    }
}
```

### Request/Response DTOs
```java
public record CreateVMRequest(
    String name,
    String node,
    int cores,
    long memoryMB,
    long diskGB,
    String network,
    String template
) {}

public record VMResponse(
    String id,
    String name,
    String node,
    String status,
    VMResources resources
) {}
```

## Testing Requirements
- Mock ProxmoxClient for unit tests
- Integration tests against test Proxmox instance
- Load testing with concurrent requests
- Verify virtual thread usage under load

## Performance Considerations
- Connection pooling for Proxmox API
- Implement caching for read operations
- Set appropriate timeouts
- Monitor thread pool behavior

## Deliverables
1. All core endpoints implemented
2. Service layer with business logic
3. Proper error handling
4. Request/Response DTOs
5. Unit and integration tests
6. API documentation updated

## Success Criteria
- All endpoints return correct data
- Virtual threads handle concurrent requests efficiently
- Errors are properly handled and logged
- API follows REST best practices
- Performance meets requirements (sub-second responses)