-- Add node maintenance tracking table
CREATE TABLE node_maintenance (
    id BIGINT PRIMARY KEY,
    node_name VARCHAR(255) NOT NULL,
    in_maintenance BOOLEAN NOT NULL DEFAULT false,
    maintenance_started TIMESTAMP,
    maintenance_ended TIMESTAMP,
    reason TEXT,
    initiated_by VARCHAR(255),
    last_drain_id VARCHAR(255),
    drain_status VARCHAR(50),
    vm_list JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create sequence for node_maintenance
CREATE SEQUENCE node_maintenance_SEQ START WITH 1 INCREMENT BY 50;

-- Create index for node lookups
CREATE INDEX idx_node_maintenance_node ON node_maintenance(node_name);

-- Create index for active maintenance
CREATE INDEX idx_node_maintenance_active ON node_maintenance(node_name, in_maintenance) WHERE in_maintenance = true;

-- Create index for maintenance history
CREATE INDEX idx_node_maintenance_history ON node_maintenance(node_name, maintenance_started DESC);
