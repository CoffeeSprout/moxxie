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
The application uses an `@AutoAuthenticate` interceptor that automatically injects authentication tickets:
- Service classes annotated with `@AutoAuthenticate` have their methods intercepted
- The interceptor looks for the **last String parameter** in a method and assumes it's the ticket parameter
- When calling these methods from Resources, pass `null` for the ticket parameter
- The interceptor will automatically inject a valid ticket from `TicketManager`

**Important**: When adding new methods to services:
- Follow the pattern of existing methods - ticket should be the last parameter
- If your method has String parameters (like node names), ensure the ticket is the final String parameter
- Example: `getVMConfig(String node, int vmId, String ticket)` - NOT `getVMConfig(String node, int vmId)`

## Configuration

The application is configured to trust all SSL certificates for development (`quarkus.tls.trust-all=true`). This should be changed for production deployments.

Default Proxmox API endpoint is configured in `application.properties`:
```
quarkus.rest-client.proxmox-api.url=https://10.0.0.10:8006/api2/json
```

## Common Issues and Solutions

### REST Client Parameter Ordering
If you encounter errors where authentication tickets appear in place of other parameters (e.g., node names), this is likely due to the `AuthenticationInterceptor` pattern:

**Symptom**: Error messages like:
```
Method 'GET /nodes/PVE:user@pve:TOKEN.../qemu/123/config' not implemented
```

**Cause**: The interceptor replaces the last String parameter with the authentication ticket. If your method doesn't follow the expected pattern, it may replace the wrong parameter.

**Solution**: Ensure all service methods that need authentication follow this pattern:
```java
// Correct - ticket is the last String parameter
public SomeResponse doSomething(String node, int id, String ticket) 

// Incorrect - will replace 'node' with ticket
public SomeResponse doSomething(String node, int id)