# Infrastructure Orchestrator Database Schema Design

## Overview

This schema implements a fully normalized relational database design for a multi-provider infrastructure orchestrator. It avoids JSON blobs in favor of proper relational structures while maintaining flexibility for provider-specific attributes.

## Key Design Decisions

### 1. Provider Polymorphism Through Type Tables

Instead of using inheritance or JSON blobs, the schema uses a **type-based approach**:

```sql
provider_types -> providers -> provider_endpoints
```

- **provider_types**: Defines the provider categories (Proxmox, Hetzner, etc.)
- **providers**: Specific provider instances
- **provider_endpoints**: Multiple endpoints per provider (regions, clusters)

This allows:
- Adding new provider types without schema changes
- Multiple instances of the same provider type
- Provider-specific behavior through type lookups

### 2. Entity-Attribute-Value (EAV) for Provider-Specific Attributes

The schema uses a **controlled EAV pattern** for resource attributes:

```sql
attribute_definitions -> resource_type_attributes -> resource_attributes
```

Benefits:
- **No JSON blobs**: All attributes are properly typed and indexed
- **Provider flexibility**: Each provider can have unique attributes
- **Type safety**: Attributes have defined data types and validation
- **Discoverability**: Can query which attributes exist for any resource type

Example: A Proxmox VM might have "cpu_type" and "numa_enabled" attributes, while a Hetzner VM has "server_type" and "placement_group".

### 3. Resource Abstraction with Specialized Tables

The schema uses a **base table with extensions pattern**:

```sql
resources (base) -> virtual_machines (specialized)
                -> networks (specialized)
                -> storage_volumes (specialized)
```

This provides:
- Common fields for all resources (name, status, provider)
- Type-specific fields in extension tables
- Ability to query all resources or specific types
- Maintains referential integrity

### 4. Relationship Management

Resources can have complex relationships tracked in a **many-to-many relationship table**:

```sql
resource_relationships (parent_id, child_id, relationship_type_id)
```

Relationship types include:
- **contains**: VM contains disk
- **depends_on**: Service depends on database
- **member_of**: VM is member of cluster
- **attached_to**: Volume attached to VM

### 5. State and History Tracking

The schema implements **comprehensive audit trails**:

1. **State History**: Tracks all state transitions
2. **Attribute History**: Tracks all attribute changes
3. **Operations Log**: Records all operations performed

This enables:
- Full audit trail for compliance
- Rollback capabilities
- Debugging and troubleshooting
- Trend analysis

### 6. Datacenter and Location Management

Physical infrastructure is modeled separately from logical resources:

```sql
locations -> datacenters -> provider_datacenters
```

This allows:
- Providers to span multiple datacenters
- Datacenters to host multiple providers
- Geographic queries and constraints

## Query Examples

### 1. Find all VMs across all providers
```sql
SELECT 
    vm.*,
    r.name,
    p.name as provider,
    pt.name as provider_type
FROM virtual_machines vm
JOIN resources r ON vm.resource_id = r.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
WHERE r.deleted_at IS NULL;
```

### 2. Get all attributes for a specific resource
```sql
SELECT 
    ad.name,
    ad.display_name,
    COALESCE(
        ra.value_string,
        ra.value_integer::text,
        ra.value_decimal::text,
        ra.value_boolean::text,
        ra.value_datetime::text
    ) as value,
    ad.unit
FROM resource_attributes ra
JOIN attribute_definitions ad ON ra.attribute_definition_id = ad.id
WHERE ra.resource_id = ?;
```

### 3. Find resources with specific attributes
```sql
-- Find all VMs with more than 8 CPU cores
SELECT r.*
FROM resources r
JOIN resource_attributes ra ON r.id = ra.resource_id
JOIN attribute_definitions ad ON ra.attribute_definition_id = ad.id
WHERE ad.name = 'cpu_cores' 
  AND ra.value_integer > 8
  AND r.resource_type_id = (SELECT id FROM resource_types WHERE name = 'vm');
```

### 4. Track resource relationships
```sql
-- Find all resources that depend on a specific database
SELECT 
    child.*,
    rt.name as relationship
FROM resource_relationships rr
JOIN resources child ON rr.child_resource_id = child.id
JOIN relationship_types rt ON rr.relationship_type_id = rt.id
WHERE rr.parent_resource_id = ?
  AND rt.name = 'depends_on';
```

## Scalability Considerations

1. **Partitioning**: The schema is designed to support partitioning:
   - `resources` table by `created_at` or `provider_endpoint_id`
   - History tables by `created_at`

2. **Indexing**: Strategic indexes on:
   - Foreign keys for JOIN performance
   - Status fields for filtering
   - Timestamp fields for time-based queries

3. **Views**: Pre-built views for common query patterns reduce complexity

4. **Archive Strategy**: Soft deletes with `deleted_at` allow for data archival

## Security Considerations

1. **Credential Isolation**: Credentials stored in separate table for access control
2. **Encryption**: Credential values should be encrypted at rest
3. **Audit Trail**: All changes are tracked with user attribution
4. **Row-Level Security**: Can be implemented based on provider/datacenter access

## Extensibility

The schema is designed for easy extension:

1. **New Provider Types**: Add row to `provider_types`
2. **New Resource Types**: Add row to `resource_types`
3. **New Attributes**: Add rows to `attribute_definitions`
4. **New Relationships**: Add rows to `relationship_types`

No schema changes required for these common operations.

## Advantages Over JSON Blob Approach

1. **Queryability**: Can search and filter by any attribute
2. **Type Safety**: Attributes have defined types and validation
3. **Indexing**: Can index specific attributes for performance
4. **Referential Integrity**: Foreign keys ensure data consistency
5. **Discoverability**: Can query available attributes and their types
6. **Change Tracking**: Individual attribute changes are tracked
7. **Performance**: Optimized queries instead of JSON parsing