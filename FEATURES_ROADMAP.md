# Moxxie Features Roadmap

This document outlines the current functionality and planned features for Moxxie, a Quarkus-based CLI and API application for managing Proxmox virtual environments.

## Current Features (v1.0.0)

### ✅ Virtual Machine Management
- **List VMs** with advanced filtering (by node, pool, tags, status, name pattern)
- **Create VMs** with full configuration options
  - Multiple disks with different storage backends
  - Multiple network interfaces with VLAN support
  - Custom CPU, memory, and boot configurations
  - Automatic VMID assignment or manual specification
- **Cloud-Init VM Creation** from templates
  - SSH key injection (with proper double-encoding)
  - Network configuration (static IP, DNS)
  - User and password setup
- **VM Power Management**
  - Start, stop, shutdown, reboot, reset
  - Suspend and resume operations
  - Force options for unresponsive VMs
- **VM Deletion** with optional purge from backups
- **VM Details** including configuration, status, and resource usage

### ✅ Snapshot Management
- **Create Snapshots** with optional VM state
- **TTL Support** for automatic expiration
- **List Snapshots** with metadata
- **Delete Snapshots** individually or by pattern
- **Rollback** to previous snapshots
- **Bulk Snapshot Operations** for multiple VMs

### ✅ Backup Management
- **List Backups** by VM, node, or storage
- **Create Backups** with compression options
- **Protected Backups** to prevent accidental deletion
- **Backup Notes** for documentation
- **Automatic Cleanup** of old backups
- **Backup Verification** status
- **Bulk Backup Operations**

### ✅ Tagging System
- **Structured Tags** with categories (client, environment, etc.)
- **Tag-based Filtering** for all operations
- **Bulk Tag Operations** (add, remove, replace)
- **Tag Expression Support** for complex queries
- **Special Tags** (always-on, maint-ok) respected by operations
- **Tag Analytics** showing usage counts

### ✅ Task Scheduler
- **Cron-based Scheduling** using Quartz
- **Snapshot Scheduling** with rotation policies
- **Multiple VM Selection Methods**
  - By tags or tag expressions
  - By VM IDs or name patterns
  - All VMs option
- **Task History** and execution tracking
- **Manual Trigger** option
- **Enable/Disable** jobs without deletion

### ✅ Bulk Operations
- **Bulk Power Operations** (start/stop/reboot multiple VMs)
- **Bulk Snapshots** with pattern-based naming
- **Bulk Backups** to specified storage
- **Dry Run Mode** for safe testing
- **Parallel Execution** options
- **Detailed Result Reporting**

### ✅ Cluster Management
- **Cluster Status** monitoring
- **Node Management** and statistics
- **Resource Overview** across cluster
- **Join Cluster** functionality
- **HA Status** information

### ✅ Storage Management
- **List Storage** with usage statistics
- **Storage Filtering** by type and content
- **ISO Upload** capability
- **Storage Health** monitoring

### ✅ Network Management
- **List Networks** (bridges, bonds, VLANs)
- **SDN Support** (Software Defined Networking)
  - VNet management
  - Zone configuration
  - Subnet management

### ✅ Migration
- **Live Migration** support
- **Offline Migration** for stopped VMs
- **Local Disk Migration** option
- **Progress Tracking**
- **Target Storage** selection

### ✅ Console Access
- **NoVNC Console** URL generation
- **SPICE Console** support
- **WebSocket Console** for web UI integration
- **Secure Ticket** generation

### ✅ Administration
- **Audit Logging** of all operations
- **Safe Mode** to prevent dangerous operations
- **Task Management** and monitoring
- **Health Checks** for all components
- **Debug Endpoints** for troubleshooting

### ✅ Developer Features
- **REST API** with comprehensive endpoints
- **OpenAPI Documentation** (via Quarkus)
- **Virtual Thread Support** for scalability
- **Structured Logging** with Proxmox API details
- **CLI Interface** using Picocli
- **YAML Output** for configuration management

## Upcoming Features (v1.1.0) - Q3 2024

### 🚧 Enhanced Scheduler Tasks
- **Backup Scheduling** with retention policies
- **Power Scheduling** (scheduled start/stop)
- **Maintenance Windows** with automatic operations
- **Resource Optimization** scheduling
- **Conditional Execution** based on VM state

### 🚧 Template Management
- **Template Creation** from existing VMs
- **Template Library** with versioning
- **Cloud-Init Template** customization
- **Template Cloning** with modifications
- **Template Marketplace** integration

### 🚧 Advanced Monitoring
- **Performance Metrics** collection
- **Resource Usage Trends**
- **Alerting System** with thresholds
- **Prometheus Integration**
- **Grafana Dashboards**

### 🚧 Security Enhancements
- **API Authentication** (API keys)
- **RBAC Implementation**
- **Audit Trail Enhancement**
- **Encryption at Rest** for sensitive data
- **Security Scanning** integration

## Planned Features (v1.2.0) - Q4 2024

### 📋 Automation Framework
- **Workflow Engine** for complex operations
- **Event-Driven Automation**
- **Custom Scripts** execution
- **Webhook Integration**
- **Policy-Based Actions**

### 📋 Disaster Recovery
- **Automated Backup Testing**
- **DR Site Replication**
- **Recovery Orchestration**
- **RTO/RPO Monitoring**
- **Failover Automation**

### 📋 Cost Management
- **Resource Cost Tracking**
- **Chargeback Reports**
- **Budget Alerts**
- **Optimization Recommendations**
- **Multi-Currency Support**

### 📋 Multi-Cluster Support
- **Cross-Cluster Operations**
- **Federated Management**
- **Global Resource View**
- **Cross-Cluster Migration**
- **Unified Monitoring**

## Future Considerations (v2.0.0)

### 🔮 AI/ML Integration
- **Predictive Resource Scaling**
- **Anomaly Detection**
- **Automated Troubleshooting**
- **Capacity Planning AI**
- **Performance Optimization ML**

### 🔮 Container Support
- **LXC Advanced Management**
- **Kubernetes Integration**
- **Container Registry**
- **Docker Compose Support**
- **Container Orchestration**

### 🔮 Advanced Networking
- **BGP Configuration UI**
- **VXLAN Management**
- **Network Policies**
- **Traffic Analysis**
- **QoS Management**

### 🔮 Compliance & Governance
- **Compliance Scanning**
- **Policy Enforcement**
- **Configuration Drift Detection**
- **Regulatory Reports**
- **ISO 27001 Support**

## Feature Development Guidelines

### Priority Matrix

| Priority | Criteria |
|----------|----------|
| **P0** | Security fixes, data loss prevention |
| **P1** | Core functionality, major user impact |
| **P2** | Performance, quality of life improvements |
| **P3** | Nice-to-have, experimental features |

### Feature States

- ✅ **Completed**: Fully implemented and tested
- 🚧 **In Progress**: Currently under development
- 📋 **Planned**: Scheduled for implementation
- 🔮 **Future**: Under consideration
- ❌ **Deprecated**: Scheduled for removal

### Development Process

1. **Feature Request** → Issue creation with use cases
2. **Design Review** → Architecture and API design
3. **Implementation** → Development with tests
4. **Testing** → Unit, integration, and manual testing
5. **Documentation** → API docs and user guides
6. **Release** → Staged rollout with monitoring

## Breaking Changes Policy

- Breaking changes only in major versions (x.0.0)
- Deprecation warnings for at least one minor version
- Migration guides for all breaking changes
- Backward compatibility mode when feasible

## Community Requested Features

Track community requests at: https://github.com/coffeesprout/moxxie/issues

Top requested features:
1. Web UI (separate project planned)
2. Mobile app for monitoring
3. Terraform provider
4. Ansible modules
5. PowerShell module

## Integration Roadmap

### Current Integrations
- Proxmox VE API (v8.0+)
- Quartz Scheduler
- PostgreSQL Database

### Planned Integrations
- **Q3 2024**: Prometheus, Grafana
- **Q4 2024**: Slack, Teams, Discord
- **Q1 2025**: Terraform, Ansible
- **Q2 2025**: ServiceNow, Jira

## Performance Goals

### Current Performance
- API Response: < 200ms (p95)
- Bulk Operations: 100 VMs/minute
- Memory Usage: < 512MB
- Startup Time: < 5 seconds

### Target Performance (v2.0)
- API Response: < 100ms (p95)
- Bulk Operations: 500 VMs/minute
- Memory Usage: < 256MB
- Startup Time: < 2 seconds

## Support Matrix

### Proxmox Versions
- **Supported**: 7.4, 8.0, 8.1, 8.2
- **Testing**: 8.3 (upcoming)
- **Deprecated**: < 7.4

### Java Versions
- **Required**: Java 21+
- **Tested**: Java 21, 22
- **Native**: GraalVM 21

### Operating Systems
- **Production**: Linux (RHEL 8+, Ubuntu 20.04+)
- **Development**: Linux, macOS, Windows (WSL2)
- **Native Binary**: Linux x64, ARM64

## Release Schedule

- **Monthly**: Patch releases (x.x.1)
- **Quarterly**: Minor releases (x.1.0)
- **Annually**: Major releases (2.0.0)
- **Hotfix**: As needed for security

## How to Contribute

1. Check the [Issues](https://github.com/coffeesprout/moxxie/issues) page
2. Read [CONTRIBUTING.md](CONTRIBUTING.md)
3. Submit feature requests with use cases
4. Vote on features you want prioritized
5. Contribute code with tests and docs

## Contact

- **Email**: moxxie@coffeesprout.com
- **Discord**: [Join our server](https://discord.gg/moxxie)
- **GitHub**: [github.com/coffeesprout/moxxie](https://github.com/coffeesprout/moxxie)