# Moxxie Transformation Roadmap

## Overview

Transform Moxxie from a CLI-based multi-cluster orchestrator to a simple, distributed REST API service that manages a single Proxmox cluster per instance. Cafn8 will handle cross-location orchestration.

## Architecture Vision

```
┌─────────────────────────────────┐
│           Cafn8                 │  ← Global orchestration
│   (Central Control Plane)       │
└───────┬───────────┬─────────────┘
        │           │
   ┌────▼────┐ ┌───▼────┐
   │ Moxxie  │ │ Moxxie │  ← Simple local API (one per location)
   │  WSDC1  │ │  ADBN1 │
   └─────────┘ └────────┘
```

## Key Technology Choices

- **Framework**: Quarkus with Virtual Threads
- **API Style**: Simple REST (not reactive)
- **Authentication**: Keycloak OIDC (admin realm)
- **Database**: PostgreSQL with Hibernate ORM
- **API Documentation**: OpenAPI/Swagger

## Implementation Phases

### Phase 1: Foundation - CLI to REST Transformation (Week 1)
Transform the existing CLI application into a REST API service while maintaining core functionality.

### Phase 2: Core API Implementation (Week 2)
Implement essential endpoints for VM and node management with single-cluster focus.

### Phase 3: Authentication & Persistence (Week 3)
Add Keycloak authentication and PostgreSQL for state management.

### Phase 4: Cafn8 Integration Features (Week 4)
Add features specifically needed for Cafn8 orchestration.

### Phase 5: Production Readiness (Week 5)
Monitoring, metrics, deployment, and documentation.

## Success Criteria

1. Moxxie exposes a simple REST API for single Proxmox cluster management
2. Authentication via Keycloak admin realm
3. State persistence in PostgreSQL
4. Ready for Cafn8 integration
5. Deployable as container with health checks

## Timeline

- **Total Duration**: 5 weeks
- **MVP (Phases 1-2)**: 2 weeks
- **Production Ready**: 5 weeks

## Deployment Strategy

Each Moxxie instance will be deployed at its respective location:
- WSDC1: `moxxie-wsdc1.internal.example.com`
- ADBN1: `moxxie-adbn1.internal.example.com`
- Future locations follow same pattern