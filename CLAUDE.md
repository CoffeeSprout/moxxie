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

## Configuration

The application is configured to trust all SSL certificates for development (`quarkus.tls.trust-all=true`). This should be changed for production deployments.

Default Proxmox API endpoint is configured in `application.properties`:
```
quarkus.rest-client.proxmox-api.url=https://10.0.0.10:8006/api2/json
```