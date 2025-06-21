-- VM Migration History
-- Tracks VM migrations between nodes for audit and recovery purposes

CREATE SEQUENCE vm_migrations_SEQ START WITH 1 INCREMENT BY 50;

CREATE TABLE vm_migrations (
    id BIGINT PRIMARY KEY,
    vm_id INTEGER NOT NULL,
    vm_name VARCHAR(255),
    source_node VARCHAR(100) NOT NULL,
    target_node VARCHAR(100) NOT NULL,
    migration_type VARCHAR(50) NOT NULL, -- 'online', 'offline'
    pre_migration_state VARCHAR(50), -- 'running', 'stopped'
    post_migration_state VARCHAR(50),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    status VARCHAR(50) NOT NULL, -- 'started', 'completed', 'failed'
    error_message TEXT,
    task_upid VARCHAR(255),
    initiated_by VARCHAR(100),
    options JSONB -- Store migration options like bwlimit, storage mapping
);

-- Indexes for performance
CREATE INDEX idx_vm_migrations_vm_id ON vm_migrations(vm_id);
CREATE INDEX idx_vm_migrations_started_at ON vm_migrations(started_at DESC);
CREATE INDEX idx_vm_migrations_status ON vm_migrations(status);
CREATE INDEX idx_vm_migrations_source_node ON vm_migrations(source_node);
CREATE INDEX idx_vm_migrations_target_node ON vm_migrations(target_node);