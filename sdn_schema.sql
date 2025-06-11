-- SDN VLAN Management Schema for Moxxie
-- This schema provides persistence for VLAN allocations and SDN audit logging

-- Table to track VLAN allocations per client
CREATE TABLE IF NOT EXISTS vlan_allocations (
    client_id VARCHAR(100) PRIMARY KEY,
    vlan_tag INTEGER NOT NULL UNIQUE,
    allocated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    vnet_ids JSON, -- Array of associated VNet IDs
    description TEXT,
    CONSTRAINT vlan_tag_range CHECK (vlan_tag >= 1 AND vlan_tag <= 4094)
);

-- Index for fast VLAN lookups
CREATE INDEX idx_vlan_allocations_vlan_tag ON vlan_allocations(vlan_tag);

-- Table to audit SDN operations
CREATE TABLE IF NOT EXISTS sdn_audit_log (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operation VARCHAR(50) NOT NULL, -- CREATE_VNET, DELETE_VNET, ALLOCATE_VLAN, etc.
    client_id VARCHAR(100),
    vlan_tag INTEGER,
    vnet_id VARCHAR(100),
    zone VARCHAR(50),
    user_id VARCHAR(50),
    source_ip VARCHAR(45), -- Support IPv6
    success BOOLEAN NOT NULL,
    error_message TEXT,
    request_data JSON, -- Store full request for debugging
    response_data JSON -- Store response data
);

-- Index for efficient querying of audit log
CREATE INDEX idx_sdn_audit_log_timestamp ON sdn_audit_log(timestamp DESC);
CREATE INDEX idx_sdn_audit_log_client_id ON sdn_audit_log(client_id);
CREATE INDEX idx_sdn_audit_log_vnet_id ON sdn_audit_log(vnet_id);
CREATE INDEX idx_sdn_audit_log_operation ON sdn_audit_log(operation);

-- Table to track VNet configurations
CREATE TABLE IF NOT EXISTS vnet_configurations (
    vnet_id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    project_name VARCHAR(100),
    zone VARCHAR(50) NOT NULL,
    vlan_tag INTEGER NOT NULL,
    alias VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'active', -- active, deleted, pending
    metadata JSON, -- Additional metadata
    FOREIGN KEY (client_id) REFERENCES vlan_allocations(client_id),
    CONSTRAINT vnet_vlan_tag_range CHECK (vlan_tag >= 1 AND vlan_tag <= 4094)
);

-- Index for VNet lookups
CREATE INDEX idx_vnet_configurations_client_id ON vnet_configurations(client_id);
CREATE INDEX idx_vnet_configurations_zone ON vnet_configurations(zone);
CREATE INDEX idx_vnet_configurations_status ON vnet_configurations(status);

-- Function to update last_modified timestamp
CREATE OR REPLACE FUNCTION update_last_modified()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_modified = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update last_modified
CREATE TRIGGER update_vnet_configurations_last_modified
BEFORE UPDATE ON vnet_configurations
FOR EACH ROW
EXECUTE FUNCTION update_last_modified();

-- View to get current VLAN usage summary
CREATE VIEW vlan_usage_summary AS
SELECT 
    COUNT(DISTINCT client_id) as total_clients,
    COUNT(DISTINCT vlan_tag) as allocated_vlans,
    MIN(vlan_tag) as min_vlan,
    MAX(vlan_tag) as max_vlan,
    COUNT(DISTINCT vnet_id) as total_vnets
FROM vlan_allocations va
LEFT JOIN vnet_configurations vc ON va.client_id = vc.client_id
WHERE vc.status = 'active' OR vc.status IS NULL;

-- View to get client network summary
CREATE VIEW client_network_summary AS
SELECT 
    va.client_id,
    va.vlan_tag,
    va.allocated_at,
    COUNT(vc.vnet_id) as vnet_count,
    ARRAY_AGG(vc.vnet_id ORDER BY vc.created_at) as vnet_ids,
    ARRAY_AGG(vc.project_name ORDER BY vc.created_at) as projects
FROM vlan_allocations va
LEFT JOIN vnet_configurations vc ON va.client_id = vc.client_id AND vc.status = 'active'
GROUP BY va.client_id, va.vlan_tag, va.allocated_at;

-- Sample data for testing (commented out)
-- INSERT INTO vlan_allocations (client_id, vlan_tag, description) VALUES 
-- ('client1', 100, 'Production client'),
-- ('client2', 101, 'Development client'),
-- ('client3', 102, 'Testing client');