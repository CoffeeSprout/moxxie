# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Moxxie is a Quarkus-based REST API service for managing Proxmox virtual environments. It provides:
- **RESTful API** endpoints for VM, storage, network, and cluster management
- **Quarkus REST Client** for Proxmox API communication
- **Scheduler System** with Quartz for automated tasks
- **Jackson** for JSON serialization
- **Java 21** as the target platform

## Key Commands

### Code Quality Commands

```bash
# Run PMD analysis (advisory only, doesn't block build)
./mvnw pmd:pmd

# Generate PMD HTML report for easier viewing
./mvnw pmd:pmd -Dformat=html
# Report will be at: target/pmd.html

# Run PMD with text output
./mvnw pmd:pmd -Dformat=text

# Use convenience scripts for PMD
./scripts/pmd-check.sh    # Full analysis with interactive report viewing
./scripts/pmd-quick.sh    # Quick analysis of changed files only

# Build with all checks (including PMD analysis)
./mvnw clean verify
```

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

### REST API Structure
- **Resource Classes**: REST endpoints organized by domain (VMResource, StorageResource, etc.)
- **Service Layer**: Business logic encapsulated in services with @ApplicationScoped
- **Dependency Injection**: Uses Quarkus CDI for injecting REST clients and services

### Proxmox Integration
- **ProxmoxClient**: MicroProfile REST client interface for Proxmox API
- **DTOs**: Data Transfer Objects for API requests/responses
- **Authentication**: Automatic ticket management via @AutoAuthenticate interceptor
- **Configuration**: Uses `application.properties` for REST client configuration

### Key Patterns
- RESTful endpoints with JAX-RS annotations
- MicroProfile REST Client for Proxmox communication
- Form-encoded POST requests for Proxmox operations
- Cookie-based authentication with PVEAuthCookie
- Automatic authentication injection via @AuthTicket

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

## API Documentation

For comprehensive API examples with working curl commands, see [API_EXAMPLES.md](./API_EXAMPLES.md). This includes examples for:
- VM management and filtering
- Snapshot creation with TTL
- Bulk operations
- Tag management
- Scheduler configuration
- Backup lifecycle management

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
      {"type": "TAG_EXPRESSION", "value": "env-prod AND NOT always-on"}
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
      {"type": "TAG_EXPRESSION", "value": "client-acme"}
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

## Code Quality and Analysis

### PMD Static Analysis

The project uses PMD for static code analysis to identify potential bugs, code smells, and maintain code quality standards. PMD is configured to be **advisory only** - it will not block builds but provides valuable feedback.

#### Configuration

- **PMD Version**: 7.16.0 (latest stable as of August 2025)
- **Maven Plugin Version**: 3.27.0
- **Ruleset**: `pmd-ruleset.xml` - Custom rules tailored for Quarkus and our coding standards
- **Java Version**: Configured for Java 21
- **Execution**: Runs during `verify` phase but doesn't fail the build
- **Cache**: PMD uses analysis cache for faster incremental analysis

#### Key Rules Categories

1. **Best Practices** - Common Java best practices
2. **Code Style** - Naming conventions, formatting
3. **Design** - Complexity metrics, coupling, cohesion
4. **Error Prone** - Common bugs and mistakes
5. **Performance** - Performance anti-patterns
6. **Security** - Security vulnerabilities
7. **Custom Rules** - Project-specific patterns:
   - Use ProxmoxException instead of RuntimeException
   - Use constants instead of magic numbers
   - Use @AuthTicket annotation for authentication parameters

#### Excluded Rules

Some PMD rules are disabled as they conflict with Quarkus patterns or our architecture:
- `GuardLogStatement` - Quarkus/JBoss logging handles this
- `LawOfDemeter` - REST endpoints often chain calls
- `DataClass` - DTOs are data classes by design
- `TooManyMethods` - Resource classes can have many endpoints

#### Running PMD

```bash
# Quick check during development (generates XML report)
./mvnw pmd:pmd

# Use convenience scripts
./scripts/pmd-check.sh  # Full analysis with report
./scripts/pmd-quick.sh  # Check only changed files

# PMD runs automatically during build
./mvnw clean verify
```

#### Addressing PMD Findings

When PMD reports issues:
1. **Review the finding** - Understand why PMD flagged it
2. **Fix if valid** - Most findings indicate real improvements
3. **Suppress if false positive** - Use `@SuppressWarnings("PMD.RuleName")` with justification comment
4. **Update ruleset** - If a rule consistently gives false positives, consider adjusting it in `pmd-ruleset.xml`

Example suppression:
```java
@SuppressWarnings("PMD.AvoidDuplicateLiterals")  // These are distinct API endpoints
private static final String VM_PATH = "/api/v1/vms";
private static final String SNAPSHOT_PATH = "/api/v1/vms";
```

## Code Patterns and Best Practices

### Builder Pattern for Complex Objects

To avoid error-prone constructors with many parameters, we use the Builder pattern for complex DTOs. This pattern is especially important for objects with 10+ parameters or many optional fields.

#### Example: CloudInitVMRequest

**Problem**: Constructor with 24 parameters, many nullable
```java
// DON'T DO THIS - Error prone and hard to read
new CloudInitVMRequest(vmId, name, node, null, cores, memory, 
    null, null, null, networks, ipConfigs, null, null, 
    searchDomain, nameservers, cpuType, null, false, 
    description, tags, null);
```

**Solution**: Use CloudInitVMRequestBuilder
```java
// DO THIS - Clear, safe, and maintainable
CloudInitVMRequest request = CloudInitVMRequestBuilder
    .forCloudInit(vmId, name, targetHost, template)
    .sshKeys(sshKeys)
    .searchDomain(domain)
    .networks(networks)
    .description(description)
    .tags(tags)
    .build();

// For FCOS nodes (no cloud-init)
CloudInitVMRequest request = CloudInitVMRequestBuilder
    .forFCOS(vmId, name, targetHost, template)
    .networks(networks)
    .description(description)
    .build();
```

#### When to Use Builder Pattern

Use builders when you have:
- More than 5-6 constructor parameters
- Many optional parameters
- Parameters of the same type (easy to mix up order)
- Need for different construction patterns (FCOS vs cloud-init)

#### Creating New Builders

When creating a new builder:
1. Create a separate `*Builder` class
2. Provide static factory methods for common cases
3. Include validation in the `build()` method
4. Keep the original constructor but consider making it package-private

Example template:
```java
public class MyRequestBuilder {
    // Required fields
    private String required1;
    private Integer required2;
    
    // Optional fields with defaults
    private String optional1 = "default";
    private Boolean optional2 = false;
    
    private MyRequestBuilder() {} // Private constructor
    
    public static MyRequestBuilder builder() {
        return new MyRequestBuilder();
    }
    
    public static MyRequestBuilder forCommonCase(String req1, Integer req2) {
        return builder()
            .required1(req1)
            .required2(req2)
            .optional1("common-value");
    }
    
    // Builder methods...
    
    public MyRequest build() {
        // Validation
        if (required1 == null) {
            throw new IllegalStateException("required1 is required");
        }
        return new MyRequest(required1, required2, optional1, optional2);
    }
}
```

### Code Quality Utilities and Patterns

#### Constants Classes

All magic numbers and strings should be defined in the appropriate constants class:

**VMConstants**: VM-related constants organized by category
```java
import com.coffeesprout.constants.VMConstants;

// Use constants instead of magic numbers
if (vmId < VMConstants.Resources.MIN_VM_ID) { // Not: if (vmId < 100)
    throw new IllegalArgumentException("VM ID too low");
}

// Status checks
if (VMConstants.Status.RUNNING.equals(vm.status())) { // Not: if ("running".equals(vm.status()))
    // VM is running
}

// Disk limits
if (slot >= VMConstants.Disk.MAX_SCSI_DEVICES) { // Not: if (slot >= 31)
    throw new IllegalArgumentException("SCSI slot out of range");
}
```

**ProxmoxConstants**: Proxmox API-specific constants
```java
import com.coffeesprout.constants.ProxmoxConstants;

// Form fields
formData.append(ProxmoxConstants.FormFields.CORES, "4"); // Not: formData.append("cores", "4")

// Headers
headers.put(ProxmoxConstants.Headers.CONTENT_TYPE, 
           ProxmoxConstants.Headers.APPLICATION_JSON);
```

#### ResourceHelperService

Use ResourceHelperService for common VM operations instead of duplicating code:

```java
@Inject
ResourceHelperService resourceHelper;

// Finding VMs - DON'T duplicate this pattern
// BAD:
VMResponse vm = vmService.listVMs(ticket).stream()
    .filter(v -> v.vmid() == vmId)
    .findFirst()
    .orElseThrow(() -> new RuntimeException("VM not found"));

// GOOD: Use ResourceHelperService
VMResponse vm = resourceHelper.findVMByIdOrThrow(vmId, ticket);

// Validate VM status before operations
resourceHelper.validateVMStatus(vm, VMConstants.Status.STOPPED, "clone");

// Check if VM is Moxxie-managed (for safe mode)
if (resourceHelper.isMoxxieManaged(vm)) {
    // Perform Moxxie-specific operations
}

// Find VMs by pattern
List<VMResponse> clientVMs = resourceHelper.findVMsByNamePattern("client-*", ticket);
```

#### Enhanced Exception Messages

Use ProxmoxException factory methods for consistent, helpful error messages:

```java
// BAD: Generic exceptions with poor context
throw new RuntimeException("VM not found: " + vmId);

// GOOD: Specific exceptions with context and suggestions
throw ProxmoxException.notFound("VM", String.valueOf(vmId), 
    "Available VMs: " + availableIds);

// VM operation failures with full context
throw ProxmoxException.vmOperationFailed("start", vmId, vm.name(),
    "VM is locked by another operation", 
    "Wait for the current operation to complete or use --force");

// Configuration errors with guidance
throw ProxmoxException.invalidConfiguration("network", 
    "VLAN ID 5000 is out of range",
    "VLAN IDs must be between 1 and 4094");

// Resource limits with details
throw ProxmoxException.resourceLimitExceeded("CPU cores", 
    requested, VMConstants.Resources.MAX_CORES);
```

#### Test Base Classes

When writing tests, extend the appropriate base class:

```java
// For REST Resource tests
public class VMResourceTest extends BaseResourceTest {
    @Override
    protected void setupMocks() {
        // Your additional mock setup
        List<VMResponse> vms = createMockVMList(5);
        when(vmService.listVMs(anyString())).thenReturn(vms);
    }
    
    @Test
    void testListVMs() {
        // Use helper methods from base class
        mockSuccessfulTask("UPID:test:123");
        // Test implementation
    }
}

// For Service tests
public class BackupServiceTest extends BaseServiceTest {
    @Override
    protected void setupMocks() {
        // Use storage mock helpers
        StorageResponse storage = createMockStorageResponse(true, true);
        when(proxmoxClient.getStorage(anyString())).thenReturn(storage);
    }
}
```

## Common Issues and Solutions

### SSH Key Double Encoding Solution

**Fixed**: SSH keys now work correctly in cloud-init VM creation. The Proxmox API requires SSH keys to be double URL encoded, as confirmed by Proxmox staff.

**Solution Implemented**:
- SSH keys are automatically double URL encoded when creating VMs
- Spaces are encoded as `%20` (not `+`) to match Python's `quote()` behavior
- The fix is transparent to API users - just pass SSH keys normally

**API Endpoints**:
1. **During VM Creation**: Include `sshKeys` in cloud-init VM creation request
2. **Update Existing VM**: `PUT /api/v1/vms/{vmId}/ssh-keys` with JSON body:
   ```json
   {
     "sshKeys": "ssh-ed25519 AAAAC3... user@host"
   }
   ```

**Technical Details**: See commit history for the investigation and fix implementation.

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

## Proxmox Client Logging

Moxxie includes comprehensive logging for all Proxmox API interactions to help debug issues. The `ProxmoxClientLoggingFilter` automatically logs request and response details.

### Features
- Logs all HTTP methods, URLs, and headers
- Captures full request bodies (for POST/PUT requests)
- Captures full response bodies (especially useful for error details)
- Automatically logs error responses at ERROR level
- Respects configured body size limits (default 10KB)

### Configuration
The logging is configured in `application.properties`:
```properties
# Enable REST client logging
quarkus.log.category."org.eclipse.microprofile.rest.client".level=DEBUG
quarkus.rest-client.logging.scope=request-response
quarkus.rest-client.logging.body-limit=10000
# Enable Proxmox client logging filter
quarkus.log.category."com.coffeesprout.client.ProxmoxClientLoggingFilter".level=INFO
```

### Example Log Output
```
09:42:26 INFO  [co.co.cl.ProxmoxClientLoggingFilter] === Proxmox API Request ===
09:42:26 INFO  [co.co.cl.ProxmoxClientLoggingFilter] Method: POST https://10.0.0.10:8006/api2/json/nodes/hv7/qemu
09:42:26 INFO  [co.co.cl.ProxmoxClientLoggingFilter] Headers: [Content-Type=application/x-www-form-urlencoded, Cookie=PVEAuthCookie=...]
09:42:26 INFO  [co.co.cl.ProxmoxClientLoggingFilter] Request Body: {net0=[virtio,bridge=vmbr0], cores=[2], memory=[4096], ...}
09:42:26 ERROR [co.co.cl.ProxmoxClientLoggingFilter] Response Body (Error): {"data":null,"errors":{"boot":"invalid format - format error\nboot.legacy: value does not match the regex pattern\n"}}
```

### Benefits
- **Detailed Error Messages**: Instead of generic "Parameter verification failed", you see exactly which parameter failed and why
- **Request Debugging**: See the exact parameters being sent to Proxmox
- **Performance Monitoring**: Track response times and payload sizes
- **Audit Trail**: Full record of all API interactions

### Usage Tips
- Error responses (4xx, 5xx) are logged at ERROR level for easy filtering
- Large response bodies are truncated to the configured limit
- Sensitive data like passwords in form parameters are visible in logs - be cautious in production
- Use `grep "Proxmox API"` to filter all Proxmox interactions in logs

## OKD/OpenShift Cluster Support

Moxxie supports provisioning OKD (OpenShift Kubernetes Distribution) clusters on Proxmox. OKD uses Fedora CoreOS (FCOS) which requires special handling as it uses ignition files instead of cloud-init.

### Key Differences for OKD

1. **FCOS nodes don't use cloud-init** - Moxxie automatically detects FCOS templates and skips cloud-init disk creation
2. **Ignition files are configured manually** - After VM creation, configure ignition on each node before starting
3. **Special node roles** - OKD uses BOOTSTRAP (temporary) and BASTION (installer) nodes
4. **No auto-start** - FCOS nodes are never started automatically

### OKD Provisioning Workflow

1. **Create FCOS template** (VM ID 10799 on storage01):
```bash
# Download Fedora CoreOS
wget https://builds.coreos.fedoraproject.org/prod/streams/stable/builds/latest/x86_64/fedora-coreos-*-qemu.x86_64.qcow2.xz
xz -d fedora-coreos-*.qcow2.xz

# Create template
qm create 10799 --name fcos-template --memory 4096 --cores 2 --net0 virtio,bridge=sdbdev
qm importdisk 10799 fedora-coreos-*.qcow2 local-zfs
qm set 10799 --scsi0 local-zfs:vm-10799-disk-0
qm set 10799 --boot c --bootdisk scsi0
qm set 10799 --serial0 socket --vga serial0
qm template 10799
```

2. **Provision cluster with Moxxie**:
```bash
curl -X POST http://localhost:8080/api/v1/clusters/provision \
  -H "Content-Type: application/json" \
  -d @examples/cluster-provision-okd.json
```

3. **Configure bastion and generate ignition**:
```bash
ssh admin@10.1.107.5  # Bastion host
./openshift-install create ignition-configs --dir=.
python3 -m http.server 8080  # Serve ignition files
```

4. **Apply ignition to each FCOS VM**:
```bash
# For each FCOS node (bootstrap, masters)
qm set <vmid> --args "-fw_cfg name=opt/com.coreos/config,string='{\"ignition\":{\"config\":{\"replace\":{\"source\":\"http://10.1.107.5:8080/bootstrap.ign\"}}}}'"
```

5. **Start nodes in sequence**:
```bash
qm start <bootstrap-vmid>
./openshift-install wait-for bootstrap-complete
qm start <master-vmids>
./openshift-install wait-for install-complete
qm destroy <bootstrap-vmid>  # Bootstrap is temporary
```

### Configuration Example

See `examples/cluster-provision-okd.json` for a complete OKD cluster configuration using:
- VLAN 107 (sdbdev) for cluster network
- VM IDs 10700-10799 range
- Bastion with dual-homed networking (public + private)
- Bootstrap and master nodes on private network only

## Tagging System

Moxxie uses a structured tagging system for VM organization and automation. When implementing new features, consider if they should:

1. **Add new tags automatically** - e.g., backup features might add `backup-daily` tags
2. **Query VMs by tags** - e.g., maintenance features should respect `always-on` and `maint-ok` tags
3. **Create new tag categories** - Coordinate with the team to ensure consistency

### Standard Tag Categories

- **Ownership**: `moxxie` (managed by Moxxie)
- **Client**: `client-<name>` (e.g., `client-nixz`)
- **Environment**: `env-<env>` (e.g., `env-prod`, `env-dev`)
- **Criticality**: `always-on`, `maint-ok`
- **Kubernetes**: `k8s-controlplane`, `k8s-worker`

**Important**: Tags in Proxmox cannot contain colons (`:`). Always use dashes (`-`) instead. For example:
- ✅ Correct: `client-nixz`, `env-prod`, `role-worker`
- ❌ Incorrect: `client:nixz`, `env:prod`, `role:worker`

### Tag Colors in Proxmox UI

- Red: Critical/Production (`always-on`, `env-prod`)
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
curl "http://localhost:8080/api/v1/vms?tags=client-nixz,env-prod"

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