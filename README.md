# Moxxie - Proxmox Virtual Environment Manager

Moxxie is a powerful REST API application for managing Proxmox virtual environments. Built with Quarkus, it provides advanced features for VM management, tagging, SDN configuration, scheduled tasks, and multi-datacenter federation support.

## Features

### Core Functionality
- **VM Management**: Create, list, start, stop, and manage VMs across Proxmox clusters
- **Advanced Tagging System**: Organize VMs with structured tags (client, environment, criticality)
- **Safe Mode Protection**: Prevent accidental operations on non-Moxxie managed VMs
- **Console Access**: SPICE and VNC console support with WebSocket proxy
- **SDN Integration**: Software-defined networking with automatic VLAN allocation

### Scheduler System
- **Flexible Task Scheduling**: Quartz-based scheduler with database persistence
- **VM Selection**: Target VMs by ID, name pattern, or tag expressions
- **Built-in Tasks**:
  - `CreateSnapshotTask`: Automated VM snapshots with rotation
  - More tasks coming: backup creation, power scheduling, old snapshot cleanup

### Enterprise Features
- **Location Awareness**: Track VMs across multiple datacenters
- **Federation API**: Multi-site resource discovery and allocation (in development)
- **Backup Analytics**: Enhanced backup tracking and management
- **Resource Calculation**: Smart resource allocation across pools

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.8+
- PostgreSQL 17 (or use Dev Services)
- Access to a Proxmox VE cluster

### Configuration

Create an `application.properties` file or set environment variables:

```properties
# Proxmox connection
moxxie.proxmox.url=https://your-proxmox:8006/api2/json
moxxie.proxmox.username=moxxie@pve
moxxie.proxmox.password=your-api-token
moxxie.proxmox.verify-ssl=false

# Database (optional - uses Dev Services by default)
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/moxxie

# Instance identification
moxxie.instance.id=moxxie-prod
moxxie.instance.location=datacenter-1
```

### Running in Development Mode

```bash
# Start with live coding
./mvnw quarkus:dev

# Access the REST API
curl http://localhost:8080/api/v1/vms

# Access Swagger UI
open http://localhost:8080/q/swagger-ui
```

### Building and Running

```bash
# Build uber-jar
./mvnw package

# Run the application
java -jar target/moxxie-*-runner.jar

# Or build native executable
./mvnw package -Dnative -Dquarkus.native.container-build=true
./target/moxxie-*-runner
```

## REST API Examples

### VM Management
```bash
# List all VMs
curl http://localhost:8080/api/v1/vms

# Filter by tags
curl "http://localhost:8080/api/v1/vms?tags=client-nixz,env-prod"

# Get VM details
curl http://localhost:8080/api/v1/vms/101

# Start/Stop VMs
curl -X POST http://localhost:8080/api/v1/vms/101/start
curl -X POST http://localhost:8080/api/v1/vms/101/stop

# Create snapshot
curl -X POST http://localhost:8080/api/v1/vms/101/snapshots \
  -H "Content-Type: application/json" \
  -d '{"name": "backup-before-update"}'

# Create snapshot with TTL (auto-delete after 4 hours)
curl -X POST http://localhost:8080/api/v1/vms/101/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pre-update-snapshot",
    "description": "Before system updates",
    "ttlHours": 4
  }'
```

### Tag Management
```bash
# Get all unique tags
curl http://localhost:8080/api/v1/tags

# Get VMs by tag
curl http://localhost:8080/api/v1/tags/env-prod/vms

# Add tags to a VM
curl -X POST http://localhost:8080/api/v1/vms/101/tags \
  -H "Content-Type: application/json" \
  -d '{"tags": ["client-acme", "env-prod"]}'

# Bulk tag operations
curl -X POST "http://localhost:8080/api/v1/tags/bulk?namePattern=web-*" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "ADD",
    "tags": ["env-prod", "always-on"]
  }'
```

### Scheduler Operations
```bash
# Create a daily snapshot job
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "daily-prod-snapshots",
    "taskType": "snapshot_create",
    "cronExpression": "0 0 2 * * ?",
    "enabled": true,
    "parameters": {
      "snapshotNamePattern": "auto-{vm}-{date}",
      "maxSnapshots": "7"
    },
    "vmSelectors": [{
      "type": "TAG_EXPRESSION",
      "value": "env-prod AND NOT always-on"
    }]
  }'

# List scheduled jobs
curl http://localhost:8080/api/v1/scheduler/jobs

# Get job details with execution history
curl http://localhost:8080/api/v1/scheduler/jobs/{id}

# Trigger job manually
curl -X POST http://localhost:8080/api/v1/scheduler/jobs/{id}/trigger

# Enable/Disable jobs
curl -X POST http://localhost:8080/api/v1/scheduler/jobs/{id}/enable
curl -X POST http://localhost:8080/api/v1/scheduler/jobs/{id}/disable
```

### SDN Management
```bash
# List VNets
curl http://localhost:8080/api/v1/vnets

# Create VNet for client
curl -X POST http://localhost:8080/api/v1/vnets \
  -H "Content-Type: application/json" \
  -d '{
    "client": "acme",
    "name": "webapp"
  }'

# Apply SDN configuration
curl -X POST http://localhost:8080/api/v1/sdn/apply
```

## Tag System

Moxxie uses a structured tagging system for VM organization:

- **Ownership**: `moxxie` - Marks VMs as Moxxie-managed
- **Client**: `client-<name>` - Client identification
- **Environment**: `env-<env>` - Environment classification (prod, dev, staging)
- **Criticality**: `always-on`, `maint-ok` - Maintenance windows
- **Kubernetes**: `k8s-controlplane`, `k8s-worker` - K8s node roles

Tag expressions support boolean logic:
- `env-prod AND client-acme` - Production VMs for specific client
- `k8s-worker AND NOT always-on` - Worker nodes that can be maintained
- `env-dev OR env-test` - All non-production VMs

## Development

### Project Structure
```
src/main/java/com/coffeesprout/
├── api/              # REST endpoints
├── client/           # Proxmox API client
├── scheduler/        # Task scheduling system
├── service/          # Business logic
├── federation/       # Federation support
└── util/             # Utilities
```

### Adding New Scheduled Tasks

See [SCHEDULER_TASK_IMPLEMENTATION_GUIDE.md](SCHEDULER_TASK_IMPLEMENTATION_GUIDE.md) for detailed instructions on implementing new scheduled tasks.

### Running Tests
```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=TagUtilsTest

# Skip tests during build
./mvnw package -DskipTests
```

### API Documentation

When running in dev mode, access:
- Swagger UI: http://localhost:8080/q/swagger-ui
- OpenAPI spec: http://localhost:8080/q/openapi

## Documentation

- [CLAUDE.md](CLAUDE.md) - AI assistant instructions and project conventions
- [SCHEDULER_TASK_IMPLEMENTATION_GUIDE.md](SCHEDULER_TASK_IMPLEMENTATION_GUIDE.md) - Task implementation guide
- [FEDERATION_API.md](FEDERATION_API.md) - Federation API design and implementation

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For issues, feature requests, or questions:
- Create an issue on [GitHub](https://github.com/CoffeeSprout/moxxie/issues)
- Check existing documentation in the docs folder

## Acknowledgments

Built with:
- [Quarkus](https://quarkus.io/) - Supersonic Subatomic Java
- [Quartz](http://www.quartz-scheduler.org/) - Job scheduling
- [PostgreSQL](https://www.postgresql.org/) - Database
- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache) - Data access