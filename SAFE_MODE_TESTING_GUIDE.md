# Safe Mode Testing Guide

## Overview

Safe Mode is a critical security feature in Moxxie that prevents accidental modifications to VMs not managed by Moxxie. This guide provides comprehensive instructions for testing the Safe Mode implementation.

## Test Setup

### 1. Enable Safe Mode

Edit `src/main/resources/application.properties`:

```properties
# Enable Safe Mode
moxxie.safety.enabled=true
moxxie.safety.mode=strict
moxxie.safety.tag-name=moxxie
moxxie.safety.allow-manual-override=true
moxxie.safety.audit-log=true
```

### 2. Start the Service

```bash
# Development mode with live reload
./mvnw quarkus:dev

# Or run the built JAR
./mvnw package
java -jar target/moxxie-*-runner.jar
```

### 3. Verify Safe Mode is Active

```bash
curl http://localhost:8080/api/v1/safety/status | jq .
```

Expected response:
```json
{
  "enabled": true,
  "mode": "STRICT",
  "statistics": {
    "totalOperations": 0,
    "blockedOperations": 0,
    "overriddenOperations": 0,
    "lastBlocked": null
  }
}
```

## Testing Scripts

### Automated Test Script

Run the comprehensive test script:

```bash
./test-safe-mode-manual.sh
```

This script tests:
- Safety status and configuration endpoints
- VM creation with automatic tagging
- Operations on Moxxie-managed VMs
- Blocked operations on non-Moxxie VMs
- Force flag override functionality
- Audit logging

### Manual Testing

For specific test cases:

```bash
# Simple test script for different modes
./test-safe-mode.sh
```

## Test Scenarios

### 1. Strict Mode Testing

**Configuration:**
```properties
moxxie.safety.mode=strict
```

**Test Cases:**

1. **Create VM** - Should automatically tag as "moxxie"
   ```bash
   curl -X POST http://localhost:8080/api/v1/vms \
     -H "Content-Type: application/json" \
     -d '{"name":"test-moxxie-vm","node":"node1","cores":1,"memoryMB":512}'
   ```

2. **Modify Moxxie VM** - Should succeed
   ```bash
   curl -X POST http://localhost:8080/api/v1/vms/{vmId}/stop
   ```

3. **Modify Non-Moxxie VM** - Should be blocked (403)
   ```bash
   curl -X POST http://localhost:8080/api/v1/vms/{nonMoxxieVmId}/stop
   ```

4. **Force Override** - Should succeed
   ```bash
   curl -X DELETE "http://localhost:8080/api/v1/vms/{nonMoxxieVmId}?force=true"
   ```

### 2. Permissive Mode Testing

**Configuration:**
```properties
moxxie.safety.mode=permissive
```

**Test Cases:**

1. **Non-destructive operations** on non-Moxxie VMs - Should succeed
   ```bash
   curl -X POST http://localhost:8080/api/v1/vms/{nonMoxxieVmId}/start
   ```

2. **Destructive operations** on non-Moxxie VMs - Should be blocked
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/vms/{nonMoxxieVmId}
   curl -X POST http://localhost:8080/api/v1/vms/{nonMoxxieVmId}/stop
   ```

### 3. Audit Mode Testing

**Configuration:**
```properties
moxxie.safety.mode=audit
```

**Test Cases:**

1. **All operations** - Should succeed but be logged
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/vms/{nonMoxxieVmId}
   ```

2. **Check audit log** - Should show warnings
   ```bash
   curl http://localhost:8080/api/v1/safety/audit | jq .
   ```

## API Endpoints

### Safety Management

- `GET /api/v1/safety/status` - Current safety status and statistics
- `GET /api/v1/safety/config` - Safety configuration
- `GET /api/v1/safety/audit` - Audit log entries

### VM Operations with Safe Mode

All VM operations respect Safe Mode:
- `POST /api/v1/vms` - Creates VM and tags as "moxxie"
- `DELETE /api/v1/vms/{vmId}` - Blocked for non-Moxxie VMs
- `POST /api/v1/vms/{vmId}/stop` - Blocked for non-Moxxie VMs
- `POST /api/v1/vms/{vmId}/start` - Allowed in permissive mode
- `POST /api/v1/vms/{vmId}/tags` - Blocked for non-Moxxie VMs

Add `?force=true` to override when `allow-manual-override` is enabled.

## Unit Tests

Run the unit tests:

```bash
# Run all tests
./mvnw test

# Run specific Safe Mode tests
./mvnw test -Dtest=SafetyDecisionTest
```

## Troubleshooting

### Safe Mode Not Working

1. Check configuration:
   ```bash
   curl http://localhost:8080/api/v1/safety/config
   ```

2. Verify interceptor is loaded:
   - Check application logs for interceptor initialization
   - Ensure `@SafeMode` annotations are present on resource methods

3. Check VM tags:
   ```bash
   curl http://localhost:8080/api/v1/vms/{vmId}/tags
   ```

### Audit Log Issues

1. Verify audit logging is enabled:
   ```properties
   moxxie.safety.audit-log=true
   ```

2. Check recent entries:
   ```bash
   curl "http://localhost:8080/api/v1/safety/audit?limit=10"
   ```

## Performance Testing

Monitor performance impact:

```bash
# Time a request without Safe Mode
time curl http://localhost:8080/api/v1/vms

# Time a request with Safe Mode enabled
time curl http://localhost:8080/api/v1/vms
```

Expected overhead: < 10ms per request

## Security Considerations

1. **Force Flag** - Use sparingly and audit all uses
2. **Tag Integrity** - Protect VM tags from unauthorized modification
3. **Audit Retention** - Implement log rotation for audit entries
4. **Mode Changes** - Log and alert on safety mode changes

## Best Practices

1. Start with **audit mode** in new deployments
2. Tag existing VMs before enabling **strict mode**
3. Monitor audit logs regularly
4. Document all force flag uses
5. Test mode changes in non-production first