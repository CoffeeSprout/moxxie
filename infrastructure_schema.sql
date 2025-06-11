-- Infrastructure Orchestrator Database Schema
-- Designed for multi-provider infrastructure management with full normalization

-- =====================================================
-- CORE PROVIDER MANAGEMENT
-- =====================================================

-- Provider types enumeration
CREATE TABLE provider_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Base providers table
CREATE TABLE providers (
    id SERIAL PRIMARY KEY,
    provider_type_id INTEGER NOT NULL REFERENCES provider_types(id),
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_type_id, name)
);

-- Provider endpoints (a provider can have multiple endpoints/regions)
CREATE TABLE provider_endpoints (
    id SERIAL PRIMARY KEY,
    provider_id INTEGER NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    endpoint_url VARCHAR(500),
    region VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_id, name)
);

-- Provider credentials (separated for security)
CREATE TABLE provider_credentials (
    id SERIAL PRIMARY KEY,
    provider_endpoint_id INTEGER NOT NULL REFERENCES provider_endpoints(id) ON DELETE CASCADE,
    credential_type VARCHAR(50) NOT NULL, -- 'api_key', 'username_password', 'certificate', etc.
    credential_key VARCHAR(100) NOT NULL,
    credential_value TEXT NOT NULL, -- Should be encrypted
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    UNIQUE(provider_endpoint_id, credential_key)
);

-- =====================================================
-- DATACENTER AND LOCATION MANAGEMENT
-- =====================================================

-- Physical locations
CREATE TABLE locations (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    country_code CHAR(2),
    city VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    timezone VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Datacenters (can span multiple providers)
CREATE TABLE datacenters (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    location_id INTEGER REFERENCES locations(id),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Link providers to datacenters
CREATE TABLE provider_datacenters (
    id SERIAL PRIMARY KEY,
    provider_endpoint_id INTEGER NOT NULL REFERENCES provider_endpoints(id),
    datacenter_id INTEGER NOT NULL REFERENCES datacenters(id),
    provider_specific_id VARCHAR(100), -- Provider's internal DC identifier
    capabilities TEXT[], -- Array of capabilities like 'vm', 'storage', 'network'
    UNIQUE(provider_endpoint_id, datacenter_id)
);

-- =====================================================
-- RESOURCE TYPE DEFINITIONS
-- =====================================================

-- Resource types (VM, Container, Network, Storage, etc.)
CREATE TABLE resource_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'compute', 'network', 'storage'
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Attributes that can be associated with resource types
CREATE TABLE attribute_definitions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200),
    data_type VARCHAR(50) NOT NULL, -- 'string', 'integer', 'decimal', 'boolean', 'datetime'
    unit VARCHAR(20), -- 'GB', 'MHz', 'Mbps', etc.
    description TEXT,
    validation_regex VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Which attributes are valid for which resource type + provider combination
CREATE TABLE resource_type_attributes (
    id SERIAL PRIMARY KEY,
    resource_type_id INTEGER NOT NULL REFERENCES resource_types(id),
    provider_type_id INTEGER REFERENCES provider_types(id), -- NULL means all providers
    attribute_definition_id INTEGER NOT NULL REFERENCES attribute_definitions(id),
    is_required BOOLEAN NOT NULL DEFAULT false,
    default_value VARCHAR(500),
    min_value VARCHAR(100),
    max_value VARCHAR(100),
    allowed_values TEXT[], -- For enum-like attributes
    UNIQUE(resource_type_id, provider_type_id, attribute_definition_id)
);

-- =====================================================
-- RESOURCE INSTANCES
-- =====================================================

-- Base resources table (all resources inherit from this)
CREATE TABLE resources (
    id SERIAL PRIMARY KEY,
    resource_type_id INTEGER NOT NULL REFERENCES resource_types(id),
    provider_endpoint_id INTEGER NOT NULL REFERENCES provider_endpoints(id),
    datacenter_id INTEGER REFERENCES datacenters(id),
    name VARCHAR(200) NOT NULL,
    external_id VARCHAR(200), -- Provider's ID for this resource
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    is_managed BOOLEAN NOT NULL DEFAULT true, -- false for discovered/imported resources
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    UNIQUE(provider_endpoint_id, external_id)
);

-- Resource attributes (EAV pattern for flexibility)
CREATE TABLE resource_attributes (
    id SERIAL PRIMARY KEY,
    resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    attribute_definition_id INTEGER NOT NULL REFERENCES attribute_definitions(id),
    value_string VARCHAR(1000),
    value_integer BIGINT,
    value_decimal DECIMAL(20, 6),
    value_boolean BOOLEAN,
    value_datetime TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource_id, attribute_definition_id),
    -- Ensure only one value column is used
    CONSTRAINT single_value CHECK (
        (CASE WHEN value_string IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_integer IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_decimal IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_boolean IS NOT NULL THEN 1 ELSE 0 END +
         CASE WHEN value_datetime IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
);

-- =====================================================
-- RESOURCE RELATIONSHIPS
-- =====================================================

-- Define types of relationships between resources
CREATE TABLE relationship_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    reverse_name VARCHAR(50) NOT NULL,
    description TEXT
);

-- Actual relationships between resources
CREATE TABLE resource_relationships (
    id SERIAL PRIMARY KEY,
    relationship_type_id INTEGER NOT NULL REFERENCES relationship_types(id),
    parent_resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    child_resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    metadata JSONB, -- Small amount of relationship-specific data
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(relationship_type_id, parent_resource_id, child_resource_id),
    CHECK (parent_resource_id != child_resource_id)
);

-- =====================================================
-- SPECIALIZED RESOURCE TABLES
-- =====================================================

-- VMs (extends resources)
CREATE TABLE virtual_machines (
    resource_id INTEGER PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    cpu_cores INTEGER NOT NULL,
    memory_mb INTEGER NOT NULL,
    primary_disk_gb INTEGER,
    operating_system VARCHAR(100),
    hypervisor_type VARCHAR(50),
    is_template BOOLEAN NOT NULL DEFAULT false
);

-- Networks (extends resources)
CREATE TABLE networks (
    resource_id INTEGER PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    network_type VARCHAR(50) NOT NULL, -- 'bridge', 'vlan', 'overlay', etc.
    cidr VARCHAR(50),
    gateway_ip INET,
    dns_servers INET[],
    vlan_id INTEGER,
    is_public BOOLEAN NOT NULL DEFAULT false
);

-- Storage volumes (extends resources)
CREATE TABLE storage_volumes (
    resource_id INTEGER PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    storage_type VARCHAR(50) NOT NULL, -- 'block', 'file', 'object'
    size_gb INTEGER NOT NULL,
    iops_limit INTEGER,
    throughput_mbps INTEGER,
    encryption_type VARCHAR(50),
    is_bootable BOOLEAN NOT NULL DEFAULT false
);

-- Clusters (extends resources)
CREATE TABLE clusters (
    resource_id INTEGER PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    cluster_type VARCHAR(50) NOT NULL, -- 'kubernetes', 'proxmox', 'vmware', etc.
    version VARCHAR(50),
    node_count INTEGER NOT NULL DEFAULT 0,
    is_high_availability BOOLEAN NOT NULL DEFAULT false
);

-- =====================================================
-- STATE TRACKING AND HISTORY
-- =====================================================

-- Resource states with transition rules
CREATE TABLE resource_states (
    id SERIAL PRIMARY KEY,
    resource_type_id INTEGER NOT NULL REFERENCES resource_types(id),
    state_name VARCHAR(50) NOT NULL,
    is_terminal BOOLEAN NOT NULL DEFAULT false,
    is_error BOOLEAN NOT NULL DEFAULT false,
    allowed_transitions VARCHAR(50)[],
    UNIQUE(resource_type_id, state_name)
);

-- State change history
CREATE TABLE resource_state_history (
    id SERIAL PRIMARY KEY,
    resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    previous_state VARCHAR(50),
    new_state VARCHAR(50) NOT NULL,
    reason TEXT,
    initiated_by VARCHAR(200), -- User or system process
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Attribute change history
CREATE TABLE resource_attribute_history (
    id SERIAL PRIMARY KEY,
    resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    attribute_definition_id INTEGER NOT NULL REFERENCES attribute_definitions(id),
    old_value VARCHAR(1000),
    new_value VARCHAR(1000),
    changed_by VARCHAR(200),
    change_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- OPERATIONS AND TASKS
-- =====================================================

-- Operations that can be performed on resources
CREATE TABLE operation_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    resource_type_id INTEGER NOT NULL REFERENCES resource_types(id),
    provider_type_id INTEGER REFERENCES provider_types(id), -- NULL for generic ops
    description TEXT,
    is_destructive BOOLEAN NOT NULL DEFAULT false,
    requires_confirmation BOOLEAN NOT NULL DEFAULT false
);

-- Track operations performed on resources
CREATE TABLE resource_operations (
    id SERIAL PRIMARY KEY,
    resource_id INTEGER NOT NULL REFERENCES resources(id),
    operation_type_id INTEGER NOT NULL REFERENCES operation_types(id),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    initiated_by VARCHAR(200) NOT NULL,
    parameters JSONB,
    result JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

-- =====================================================
-- METADATA AND TAGS
-- =====================================================

-- Tag definitions
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50),
    description TEXT,
    color VARCHAR(7) -- Hex color for UI
);

-- Resource tags
CREATE TABLE resource_tags (
    resource_id INTEGER NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    added_by VARCHAR(200),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (resource_id, tag_id)
);

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================

CREATE INDEX idx_resources_type_status ON resources(resource_type_id, status);
CREATE INDEX idx_resources_provider_endpoint ON resources(provider_endpoint_id);
CREATE INDEX idx_resources_datacenter ON resources(datacenter_id);
CREATE INDEX idx_resources_created_at ON resources(created_at);
CREATE INDEX idx_resources_deleted_at ON resources(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_resource_attributes_resource ON resource_attributes(resource_id);
CREATE INDEX idx_resource_attributes_definition ON resource_attributes(attribute_definition_id);

CREATE INDEX idx_resource_relationships_parent ON resource_relationships(parent_resource_id);
CREATE INDEX idx_resource_relationships_child ON resource_relationships(child_resource_id);

CREATE INDEX idx_resource_state_history_resource ON resource_state_history(resource_id);
CREATE INDEX idx_resource_state_history_created ON resource_state_history(created_at);

CREATE INDEX idx_resource_operations_resource ON resource_operations(resource_id);
CREATE INDEX idx_resource_operations_status ON resource_operations(status);

-- =====================================================
-- VIEWS FOR COMMON QUERIES
-- =====================================================

-- Current resource state view
CREATE VIEW v_resource_current_state AS
SELECT DISTINCT ON (resource_id)
    resource_id,
    new_state as current_state,
    created_at as state_changed_at
FROM resource_state_history
ORDER BY resource_id, created_at DESC;

-- Resource with provider info
CREATE VIEW v_resources_with_provider AS
SELECT 
    r.*,
    p.name as provider_name,
    pt.name as provider_type,
    pe.name as endpoint_name,
    pe.region,
    d.name as datacenter_name
FROM resources r
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
LEFT JOIN datacenters d ON r.datacenter_id = d.id;

-- VM details view
CREATE VIEW v_virtual_machines AS
SELECT 
    r.*,
    vm.cpu_cores,
    vm.memory_mb,
    vm.primary_disk_gb,
    vm.operating_system,
    vm.hypervisor_type,
    vm.is_template,
    rcs.current_state
FROM resources r
JOIN virtual_machines vm ON r.id = vm.resource_id
LEFT JOIN v_resource_current_state rcs ON r.id = rcs.resource_id
WHERE r.deleted_at IS NULL;

-- =====================================================
-- SAMPLE DATA FOR REFERENCE
-- =====================================================

-- Insert provider types
INSERT INTO provider_types (name, display_name, description) VALUES
('proxmox', 'Proxmox VE', 'Proxmox Virtual Environment'),
('hetzner', 'Hetzner Cloud', 'Hetzner Cloud Infrastructure'),
('libvirt', 'Libvirt', 'Libvirt virtualization API'),
('bhyve', 'bhyve', 'BSD hypervisor');

-- Insert resource types
INSERT INTO resource_types (name, display_name, category) VALUES
('vm', 'Virtual Machine', 'compute'),
('container', 'Container', 'compute'),
('network', 'Network', 'network'),
('storage_volume', 'Storage Volume', 'storage'),
('cluster', 'Cluster', 'compute');

-- Insert common attribute definitions
INSERT INTO attribute_definitions (name, display_name, data_type, unit) VALUES
('cpu_type', 'CPU Type', 'string', NULL),
('cpu_sockets', 'CPU Sockets', 'integer', NULL),
('numa_enabled', 'NUMA Enabled', 'boolean', NULL),
('boot_order', 'Boot Order', 'string', NULL),
('bios_type', 'BIOS Type', 'string', NULL),
('machine_type', 'Machine Type', 'string', NULL),
('guest_agent', 'Guest Agent Enabled', 'boolean', NULL),
('start_on_boot', 'Start on Boot', 'boolean', NULL),
('protection', 'Protection Enabled', 'boolean', NULL),
('backup_enabled', 'Backup Enabled', 'boolean', NULL),
('backup_schedule', 'Backup Schedule', 'string', NULL);

-- Insert relationship types
INSERT INTO relationship_types (name, reverse_name, description) VALUES
('contains', 'contained_by', 'Parent contains child resource'),
('depends_on', 'required_by', 'Resource depends on another'),
('connects_to', 'connected_from', 'Network connection relationship'),
('member_of', 'has_member', 'Membership in a group or cluster'),
('attached_to', 'has_attached', 'Storage or device attachment');