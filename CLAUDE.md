# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Moxxie is a Quarkus-based CLI application for managing Proxmox virtual environments. It uses:
- **Quarkus** with Picocli for the CLI framework
- **REST Client** for Proxmox API communication
- **Jackson** for JSON/YAML serialization
- **Java 21** as the target platform

## Key Commands

### Development and Build Commands

```bash
# Run in development mode with live coding
./mvnw quarkus:dev

# Run in dev mode with CLI arguments
./mvnw quarkus:dev -Dquarkus.args='discover --dry-run'

# Build the application (creates uber-jar)
./mvnw package

# Run tests
./mvnw test

# Build native executable
./mvnw package -Dnative

# Build native in container (no GraalVM required)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### Running Quarkus in Background for API Testing

When testing REST API endpoints, use these commands to run Quarkus in the background:

```bash
# Start Quarkus in background
nohup ./mvnw quarkus:dev > quarkus-dev.log 2>&1 &

# Wait for startup (authentication takes a few seconds)
sleep 15

# Test endpoints with curl
curl -X GET http://localhost:8080/api/v1/vms | jq .

# Check logs if needed
tail -f quarkus-dev.log

# Stop Quarkus when done
pkill -f "java.*quarkus"
# or more specifically:
pkill -f "mvnw quarkus:dev"
```

**Important Notes:**
- Always wait 10-15 seconds after starting Quarkus for the TicketManager to authenticate with Proxmox
- The application runs on port 8080 by default
- Hot reload works automatically when you modify Java files
- Clean up log files after testing: `rm -f quarkus-dev.log nohup.out`

### Application Execution

```bash
# Run the uber-jar
java -jar target/moxxie-1.0.0-SNAPSHOT-runner.jar

# Run native executable
./target/moxxie-1.0.0-SNAPSHOT-runner
```

## Architecture

### CLI Structure
- **MainCLI**: Entry point with subcommands (discover, list, provision)
- **Command Pattern**: Each operation is implemented as a separate Command class
- **Dependency Injection**: Uses Quarkus CDI for injecting REST clients and services

### API Integration
- **ProxmoxClient**: REST client interface for Proxmox API
- **Model Classes**: DTOs for API requests/responses (LoginRequest, LoginResponse, Node, VM, etc.)
- **Configuration**: Uses `application.properties` for REST client configuration

### Key Patterns
- RESTClient interfaces use MicroProfile annotations
- Form-encoded POST requests for VM creation/operations
- Cookie-based authentication with PVEAuthCookie
- YAML output formatting for configuration files

### Authentication Pattern
The application uses an `@AutoAuthenticate` interceptor that automatically injects authentication tickets into service methods. As of December 2024, the authentication system uses annotation-based parameter injection for improved clarity and type safety.

#### How it Works:
1. Service classes are annotated with `@AutoAuthenticate` at the class level
2. Methods that need authentication have a String parameter annotated with `@AuthTicket`
3. The `AuthenticationInterceptor` automatically injects a valid ticket from `TicketManager`
4. When calling these methods from Resources, pass `null` for the ticket parameter
5. The interceptor handles authentication errors and automatically retries with a refreshed ticket

#### Example Usage:
```java
@ApplicationScoped
@AutoAuthenticate
public class VMService {
    
    public List<VMResponse> listVMs(@AuthTicket String ticket) {
        // ticket will be automatically injected
        return proxmoxClient.getVMs(ticket);
    }
    
    public VMResponse getVM(String node, int vmId, @AuthTicket String ticket) {
        // ticket parameter can be anywhere - it's identified by @AuthTicket
        return proxmoxClient.getVM(node, vmId, ticket);
    }
}
```

#### Important Guidelines:
- **Always use @AuthTicket annotation** to mark authentication parameters
- The @AuthTicket parameter can be placed anywhere in the method signature
- Only String parameters can be annotated with @AuthTicket
- For backward compatibility, the interceptor falls back to the last String parameter if no @AuthTicket is found
- When creating new services, adopt the @AutoAuthenticate pattern instead of manual ticket management

#### Migration Notes:
- Legacy code may still use position-based convention (last String parameter)
- New code should always use @AuthTicket annotation for clarity
- Services like TagService and SDNService that manually manage tickets should be migrated to use @AutoAuthenticate

## Configuration

The application is configured to trust all SSL certificates for development (`quarkus.tls.trust-all=true`). This should be changed for production deployments.

Default Proxmox API endpoint is configured in `application.properties`:
```
quarkus.rest-client.proxmox-api.url=https://10.0.0.10:8006/api2/json
```

## Scheduler System

Moxxie includes a flexible task scheduling system built on Quartz. For detailed information on implementing new scheduled tasks, see the [Scheduler Task Implementation Guide](./SCHEDULER_TASK_IMPLEMENTATION_GUIDE.md).

### Quick Examples

**Create a scheduled snapshot job:**
```bash
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "daily-snapshots",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "maxSnapshots": "7"
    },
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "env:prod AND NOT always-on"}
    ]
  }'
```

**Create pre-update snapshots with TTL and cleanup:**
```bash
# Create snapshot with 24-hour TTL
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-snapshots",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 0 1 1 ? 2099",  # Manual trigger only
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "preupd-{date}-{time}",
      "description": "Pre-update snapshot",
      "snapshotTTL": "24"  # Auto-expire after 24 hours
    },
    "vmSelectors": [
      {"type": "TAG_EXPRESSION", "value": "client:acme"}
    ]
  }'

# Schedule cleanup job to run hourly
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cleanup-temp-snapshots",
    "taskType": "snapshot_delete",
    "cronExpression": "0 0 * * * ?",
    "enabled": true,
    "parameters": {
      "ageThresholdHours": "24",
      "namePattern": "preupd-*",
      "checkDescription": "true",  # Check TTL in descriptions
      "safeMode": "true",
      "dryRun": "false"
    },
    "vmSelectors": [
      {"type": "ALL", "value": "*"}
    ]
  }'
```

**Available task types:**
- `snapshot_create` - Creates VM snapshots with optional rotation and TTL
- `snapshot_delete` - Deletes old snapshots based on age, pattern, or TTL
- `test_task` - Simple test task for verification
- More tasks coming: backup creation, power scheduling

## Snapshot TTL Feature

Snapshots can now be created with a Time-To-Live (TTL) value. This is useful for temporary snapshots that should be automatically deleted after a certain time.

**Via REST API:**
```bash
# Create snapshot with 4-hour TTL
curl -X POST http://localhost:8080/api/v1/vms/8200/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-20240621",
    "description": "Before system updates",
    "ttlHours": 4
  }'
```

The TTL is appended to the description as "(TTL: 4h)" and can be parsed by the `snapshot_delete` scheduled task when `checkDescription` is enabled.

## Common Issues and Solutions

### Authentication Parameter Issues
If you encounter errors where authentication tickets appear in place of other parameters (e.g., node names), check the following:

**Symptom**: Error messages like:
```
Method 'GET /nodes/PVE:user@pve:TOKEN.../qemu/123/config' not implemented
```

**Cause**: Either missing @AuthTicket annotation or using legacy position-based convention incorrectly.

**Solutions**:
1. **Use @AuthTicket annotation** (recommended):
```java
// Correct - explicitly mark the ticket parameter
public SomeResponse doSomething(String node, int id, @AuthTicket String ticket)
```

2. **Check annotation placement**:
```java
// Wrong - forgot @AuthTicket annotation
public SomeResponse doSomething(String node, String ticket)

// Right - with annotation, position doesn't matter
public SomeResponse doSomething(@AuthTicket String ticket, String node, int id)
```

3. **Ensure service has @AutoAuthenticate**:
```java
@ApplicationScoped
@AutoAuthenticate  // Don't forget this!
public class MyService {
    // methods here
}

## Tagging System

Moxxie uses a structured tagging system for VM organization and automation. When implementing new features, consider if they should:

1. **Add new tags automatically** - e.g., backup features might add `backup:daily` tags
2. **Query VMs by tags** - e.g., maintenance features should respect `always-on` and `maint-ok` tags
3. **Create new tag categories** - Coordinate with the team to ensure consistency

### Standard Tag Categories

- **Ownership**: `moxxie` (managed by Moxxie)
- **Client**: `client-<name>` (e.g., `client-nixz`)
- **Environment**: `env-<env>` (e.g., `env-prod`, `env-dev`)
- **Criticality**: `always-on`, `maint-ok`
- **Kubernetes**: `k8s-controlplane`, `k8s-worker`

### Tag Colors in Proxmox UI

- Red: Critical/Production (`always-on`, `env:prod`)
- Blue: Clients and environments
- Purple: Kubernetes nodes
- Green: Moxxie managed
- Orange: Maintenance allowed

### Adding New Tags

When adding new tag categories:
1. Update `TagUtils.java` with the new category
2. Add color mapping in tag style configuration
3. Document in this file
4. Consider auto-tagging rules

### Tag-aware Features

All features that perform operations on VMs should:
- Respect `always-on` tags (never auto-shutdown)
- Check `maint-ok` before maintenance operations
- Filter by client tags for multi-tenancy

### API Endpoints

**Query VMs with tag filtering:**
```bash
# Filter by multiple tags (AND logic)
curl "http://localhost:8080/api/v1/vms?tags=client:nixz,env:prod"

# Filter by client (convenience)
curl "http://localhost:8080/api/v1/vms?client=nixz"

# Get all unique tags
curl "http://localhost:8080/api/v1/tags"

# Get VMs with specific tag
curl "http://localhost:8080/api/v1/tags/client-nixz/vms"
```

**Bulk tag operations:**
```bash
# Add tags to VMs by name pattern
curl -X POST "http://localhost:8080/api/v1/tags/bulk?namePattern=nixz-*" \
  -H "Content-Type: application/json" \
  -d '{"action": "ADD", "tags": ["client-nixz", "env-prod"]}'

# Remove tags from specific VMs
curl -X POST "http://localhost:8080/api/v1/tags/bulk?vmIds=101,102,103" \
  -H "Content-Type: application/json" \
  -d '{"action": "REMOVE", "tags": ["maint-ok"]}'
```