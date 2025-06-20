-- Moxxie Scheduler Schema
-- Initial schema including Quartz tables and custom scheduling tables

-- =====================================================
-- QUARTZ TABLES (Required by Quartz Scheduler)
-- =====================================================

CREATE TABLE qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250) NULL,
    job_class_name VARCHAR(250) NOT NULL,
    is_durable BOOLEAN NOT NULL,
    is_nonconcurrent BOOLEAN NOT NULL,
    is_update_data BOOLEAN NOT NULL,
    requests_recovery BOOLEAN NOT NULL,
    job_data BYTEA NULL,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250) NULL,
    next_fire_time BIGINT NULL,
    prev_fire_time BIGINT NULL,
    priority INTEGER NULL,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NULL,
    calendar_name VARCHAR(200) NULL,
    misfire_instr SMALLINT NULL,
    job_data BYTEA NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group) 
        REFERENCES qrtz_job_details(sched_name, job_name, job_group)
);

CREATE TABLE qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) 
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) 
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_simprop_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    str_prop_1 VARCHAR(512) NULL,
    str_prop_2 VARCHAR(512) NULL,
    str_prop_3 VARCHAR(512) NULL,
    int_prop_1 INT NULL,
    int_prop_2 INT NULL,
    long_prop_1 BIGINT NULL,
    long_prop_2 BIGINT NULL,
    dec_prop_1 NUMERIC(13,4) NULL,
    dec_prop_2 NUMERIC(13,4) NULL,
    bool_prop_1 BOOLEAN NULL,
    bool_prop_2 BOOLEAN NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) 
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) 
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(95) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(200) NULL,
    job_group VARCHAR(200) NULL,
    is_nonconcurrent BOOLEAN NULL,
    requests_recovery BOOLEAN NULL,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

-- Create indexes for Quartz
CREATE INDEX idx_qrtz_j_req_recovery ON qrtz_job_details(sched_name, requests_recovery);
CREATE INDEX idx_qrtz_j_grp ON qrtz_job_details(sched_name, job_group);
CREATE INDEX idx_qrtz_t_j ON qrtz_triggers(sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_t_jg ON qrtz_triggers(sched_name, job_group);
CREATE INDEX idx_qrtz_t_c ON qrtz_triggers(sched_name, calendar_name);
CREATE INDEX idx_qrtz_t_g ON qrtz_triggers(sched_name, trigger_group);
CREATE INDEX idx_qrtz_t_state ON qrtz_triggers(sched_name, trigger_state);
CREATE INDEX idx_qrtz_t_n_state ON qrtz_triggers(sched_name, trigger_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_n_g_state ON qrtz_triggers(sched_name, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_t_next_fire_time ON qrtz_triggers(sched_name, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st ON qrtz_triggers(sched_name, trigger_state, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_misfire ON qrtz_triggers(sched_name, misfire_instr, next_fire_time);
CREATE INDEX idx_qrtz_t_nft_st_misfire ON qrtz_triggers(sched_name, misfire_instr, next_fire_time, trigger_state);
CREATE INDEX idx_qrtz_t_nft_st_misfire_grp ON qrtz_triggers(sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);
CREATE INDEX idx_qrtz_ft_trig_inst_name ON qrtz_fired_triggers(sched_name, instance_name);
CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry ON qrtz_fired_triggers(sched_name, instance_name, requests_recovery);
CREATE INDEX idx_qrtz_ft_j_g ON qrtz_fired_triggers(sched_name, job_name, job_group);
CREATE INDEX idx_qrtz_ft_jg ON qrtz_fired_triggers(sched_name, job_group);
CREATE INDEX idx_qrtz_ft_t_g ON qrtz_fired_triggers(sched_name, trigger_name, trigger_group);
CREATE INDEX idx_qrtz_ft_tg ON qrtz_fired_triggers(sched_name, trigger_group);

-- =====================================================
-- MOXXIE SCHEDULING TABLES
-- =====================================================

-- Create sequences for Hibernate
CREATE SEQUENCE task_types_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE scheduled_jobs_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE job_parameters_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE job_vm_selectors_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE job_executions_SEQ START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE job_vm_executions_SEQ START WITH 1 INCREMENT BY 50;

-- Task types enumeration
CREATE TABLE task_types (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    task_class VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Scheduled jobs configuration
CREATE TABLE scheduled_jobs (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    task_type_id BIGINT NOT NULL REFERENCES task_types(id),
    cron_expression VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    max_retries INTEGER NOT NULL DEFAULT 3,
    retry_delay_seconds INTEGER NOT NULL DEFAULT 300,
    timeout_seconds INTEGER NOT NULL DEFAULT 3600,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(200),
    updated_by VARCHAR(200)
);

-- Job configuration parameters (key-value pairs)
CREATE TABLE job_parameters (
    id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES scheduled_jobs(id) ON DELETE CASCADE,
    param_key VARCHAR(100) NOT NULL,
    param_value TEXT NOT NULL,
    UNIQUE(job_id, param_key)
);

-- VM selection criteria for jobs
CREATE TABLE job_vm_selectors (
    id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES scheduled_jobs(id) ON DELETE CASCADE,
    selector_type VARCHAR(50) NOT NULL, -- 'tag_expression', 'vm_list', 'all'
    selector_value TEXT NOT NULL, -- tag expression, comma-separated VM IDs, or 'ALL'
    exclude_expression TEXT -- optional exclusion expression
);

-- Job execution history
CREATE TABLE job_executions (
    id BIGINT PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES scheduled_jobs(id),
    execution_id VARCHAR(100) NOT NULL UNIQUE, -- Quartz fire instance ID
    status VARCHAR(50) NOT NULL, -- 'running', 'completed', 'failed', 'cancelled'
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT,
    processed_vms INTEGER DEFAULT 0,
    successful_vms INTEGER DEFAULT 0,
    failed_vms INTEGER DEFAULT 0,
    execution_details JSONB -- Store detailed results
);

-- VM-level execution details
CREATE TABLE job_vm_executions (
    id BIGINT PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES job_executions(id) ON DELETE CASCADE,
    vm_id INTEGER NOT NULL,
    vm_name VARCHAR(200),
    node_name VARCHAR(100),
    status VARCHAR(50) NOT NULL, -- 'success', 'failed', 'skipped'
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT,
    result_data JSONB -- Task-specific results
);

-- Insert default task types
INSERT INTO task_types (id, name, display_name, description, task_class) VALUES
(1, 'test', 'Test Task', 'Simple test task for verification', 'com.coffeesprout.scheduler.task.TestTask'),
(51, 'snapshot_create', 'Create Snapshot', 'Creates a snapshot of selected VMs', 'com.coffeesprout.scheduler.task.CreateSnapshotTask'),
(101, 'snapshot_delete', 'Delete Old Snapshots', 'Deletes snapshots older than specified retention', 'com.coffeesprout.scheduler.tasks.DeleteOldSnapshotsTask'),
(151, 'snapshot_rotate', 'Rotate Snapshots', 'Maintains snapshot count based on rotation policy', 'com.coffeesprout.scheduler.tasks.RotateSnapshotsTask'),
(201, 'backup_create', 'Create Backup', 'Creates a backup of selected VMs', 'com.coffeesprout.scheduler.tasks.CreateBackupTask'),
(251, 'backup_rotate', 'Rotate Backups', 'Maintains backup count based on rotation policy', 'com.coffeesprout.scheduler.tasks.RotateBackupsTask'),
(301, 'power_schedule', 'Power Schedule', 'Start/stop VMs based on schedule', 'com.coffeesprout.scheduler.tasks.PowerScheduleTask'),
(351, 'health_check', 'Health Check', 'Performs health checks on VMs', 'com.coffeesprout.scheduler.tasks.HealthCheckTask'),
(401, 'resource_cleanup', 'Resource Cleanup', 'Cleans up unused resources', 'com.coffeesprout.scheduler.tasks.ResourceCleanupTask');

-- Update sequences to start after our inserted values
ALTER SEQUENCE task_types_SEQ RESTART WITH 451;

-- Create indexes for performance
CREATE INDEX idx_scheduled_jobs_enabled ON scheduled_jobs(enabled);
CREATE INDEX idx_job_executions_job_id ON job_executions(job_id);
CREATE INDEX idx_job_executions_status ON job_executions(status);
CREATE INDEX idx_job_executions_started_at ON job_executions(started_at DESC);
CREATE INDEX idx_job_vm_executions_execution_id ON job_vm_executions(execution_id);
CREATE INDEX idx_job_vm_executions_vm_id ON job_vm_executions(vm_id);