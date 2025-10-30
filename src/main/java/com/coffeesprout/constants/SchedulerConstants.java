package com.coffeesprout.constants;

/**
 * Constants for the scheduler subsystem.
 * Centralizes scheduler-related constants and configuration values.
 */
public final class SchedulerConstants {

    private SchedulerConstants() {
        // Prevent instantiation
    }

    /**
     * Quartz job and trigger groups
     */
    public static final String JOB_GROUP = "moxxie-jobs";
    public static final String TRIGGER_GROUP = "moxxie-triggers";

    /**
     * Job data map keys
     */
    public static final class JobDataKeys {
        public static final String JOB_ID = "jobId";
        public static final String JOB_NAME = "jobName";
        public static final String EXECUTION_ID = "executionId";
        public static final String MANUAL_TRIGGER = "manualTrigger";

        private JobDataKeys() {}
    }

    /**
     * Task types
     */
    public static final class TaskTypes {
        public static final String SNAPSHOT_CREATE = "snapshot_create";
        public static final String SNAPSHOT_DELETE = "snapshot_delete";
        public static final String BACKUP_CREATE = "backup_create";
        public static final String BACKUP_DELETE = "backup_delete";
        public static final String VM_START = "vm_start";
        public static final String VM_STOP = "vm_stop";
        public static final String TEST_TASK = "test_task";

        private TaskTypes() {}
    }

    /**
     * Default job configuration
     */
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_RETRY_DELAY_SECONDS = 300;
    public static final int DEFAULT_TIMEOUT_SECONDS = 3600;

    /**
     * Cleanup configuration
     */
    public static final int EXECUTION_RETENTION_DAYS = 30;
    public static final String CLEANUP_CRON = "0 0 2 * * ?"; // 2 AM daily

    /**
     * VM Selector types
     */
    public static final class SelectorTypes {
        public static final String ALL = "ALL";
        public static final String VM_ID = "VM_ID";
        public static final String TAG_EXPRESSION = "TAG_EXPRESSION";
        public static final String NAME_PATTERN = "NAME_PATTERN";
        public static final String NODE = "NODE";
        public static final String POOL = "POOL";

        private SelectorTypes() {}
    }
}
