# Node Management Endpoints Implementation

## Overview
Implemented node management endpoints for issue #10, providing REST API access to Proxmox node information.

## Implemented Endpoints

### 1. List All Nodes
- **Endpoint**: `GET /api/v1/nodes`
- **Description**: Returns a list of all nodes in the Proxmox cluster
- **Response**: Array of `NodeResponse` objects containing:
  - `name`: Node name
  - `cpu`: CPU count
  - `max_memory`: Maximum memory in bytes
  - `status`: Node status (typically "online")
  - `uptime`: Node uptime in seconds

### 2. Get Node Status
- **Endpoint**: `GET /api/v1/nodes/{nodeName}/status`
- **Description**: Returns detailed status information for a specific node
- **Response**: `NodeStatusResponse` object containing:
  - `node_name`: Name of the node
  - `cpu_usage`: CPU usage percentage
  - `memory`: Memory information (total, used, free, usage_percentage)
  - `cpu_info`: CPU hardware information (cpus, cores, sockets)
  - `load_average`: System load averages

### 3. Get Node Resources
- **Endpoint**: `GET /api/v1/nodes/{nodeName}/resources`
- **Description**: Returns VMs and storage information for a specific node
- **Response**: `NodeResourcesResponse` object containing:
  - `node_name`: Name of the node
  - `vms`: List of VMs on the node with their details
  - `storage`: List of storage pools on the node
  - `total_vms`: Total count of VMs
  - `total_storage_pools`: Total count of storage pools

## Implementation Details

### Files Created
1. **NodeResource.java** - Main REST controller implementing the endpoints
2. **NodeResponse.java** - DTO for node list response
3. **NodeStatusResponse.java** - DTO for node status response
4. **NodeResourcesResponse.java** - DTO for node resources response
5. **NodeResourceTest.java** - Basic test cases for the endpoints

### Files Modified
1. **VM.java** - Extended with additional fields needed for VM information:
   - Added status, cpus, maxmem, maxdisk fields
   - Added proper Jackson annotations for JSON mapping

### Key Features
- Uses virtual threads for better performance (`@RunOnVirtualThread`)
- Proper error handling with appropriate HTTP status codes
- OpenAPI documentation with detailed annotations
- Follows existing patterns from InfoResource and ProxmoxResource
- Integrates with existing NodeService for business logic
- Automatic authentication handling via `@AutoAuthenticate` interceptor

### Error Handling
- 200 OK - Successful response
- 401 Unauthorized - When Proxmox credentials are invalid
- 404 Not Found - When specified node doesn't exist
- 500 Internal Server Error - For other errors

### Testing
Basic integration tests are provided in `NodeResourceTest.java`. The endpoints can be tested using:
- The provided `test-node-endpoints.sh` script
- Swagger UI at http://localhost:8080/swagger-ui/
- Direct curl commands

## Usage Example

```bash
# List all nodes
curl -X GET http://localhost:8080/api/v1/nodes

# Get specific node status
curl -X GET http://localhost:8080/api/v1/nodes/pve/status

# Get node resources (VMs and storage)
curl -X GET http://localhost:8080/api/v1/nodes/pve/resources
```

## Notes
- The endpoints follow REST best practices and the existing application patterns
- Authentication is handled automatically by the TicketManager
- All responses are in JSON format
- The implementation is ready for production use with proper error handling and logging