package com.coffeesprout.scheduler.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder for ScheduledJob entity to simplify job creation and configuration.
 * Provides fluent API for creating scheduled jobs with parameters and VM selectors.
 */
public class ScheduledJobBuilder {

    private String name;
    private String description;
    private TaskType taskType;
    private String cronExpression;
    private boolean enabled = true;
    private int maxRetries = 3;
    private int retryDelaySeconds = 300;
    private int timeoutSeconds = 3600;
    private String createdBy;
    private String updatedBy;

    private final Map<String, String> parameters = new HashMap<>();
    private final Map<String, VMSelector> vmSelectors = new HashMap<>();

    private ScheduledJobBuilder() {}

    /**
     * Create a new builder instance
     */
    public static ScheduledJobBuilder builder() {
        return new ScheduledJobBuilder();
    }

    /**
     * Factory method for snapshot creation jobs
     */
    public static ScheduledJobBuilder forSnapshotCreation(String name, String cronExpression) {
        return builder()
            .name(name)
            .cronExpression(cronExpression)
            .description("Automated snapshot creation job")
            .maxRetries(2)
            .timeoutSeconds(1800);
    }

    /**
     * Factory method for snapshot deletion/cleanup jobs
     */
    public static ScheduledJobBuilder forSnapshotCleanup(String name, String cronExpression) {
        return builder()
            .name(name)
            .cronExpression(cronExpression)
            .description("Automated snapshot cleanup job")
            .maxRetries(3)
            .timeoutSeconds(3600);
    }

    /**
     * Factory method for backup jobs
     */
    public static ScheduledJobBuilder forBackup(String name, String cronExpression) {
        return builder()
            .name(name)
            .cronExpression(cronExpression)
            .description("Automated backup job")
            .maxRetries(1)
            .timeoutSeconds(7200);
    }

    /**
     * Factory method for power management jobs
     */
    public static ScheduledJobBuilder forPowerManagement(String name, String cronExpression) {
        return builder()
            .name(name)
            .cronExpression(cronExpression)
            .description("Automated power management job")
            .maxRetries(3)
            .retryDelaySeconds(60)
            .timeoutSeconds(600);
    }

    // Required fields
    public ScheduledJobBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ScheduledJobBuilder taskType(TaskType taskType) {
        this.taskType = taskType;
        return this;
    }

    public ScheduledJobBuilder cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    // Optional fields
    public ScheduledJobBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ScheduledJobBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ScheduledJobBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public ScheduledJobBuilder retryDelaySeconds(int retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
        return this;
    }

    public ScheduledJobBuilder timeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public ScheduledJobBuilder createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public ScheduledJobBuilder updatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    // Parameter management
    public ScheduledJobBuilder parameter(String key, String value) {
        this.parameters.put(key, value);
        return this;
    }

    public ScheduledJobBuilder parameters(Map<String, String> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    // Common parameters
    public ScheduledJobBuilder snapshotNamePattern(String pattern) {
        return parameter("snapshotNamePattern", pattern);
    }

    public ScheduledJobBuilder maxSnapshots(int max) {
        return parameter("maxSnapshots", String.valueOf(max));
    }

    public ScheduledJobBuilder snapshotTTL(int hours) {
        return parameter("snapshotTTL", String.valueOf(hours));
    }

    public ScheduledJobBuilder ageThresholdHours(int hours) {
        return parameter("ageThresholdHours", String.valueOf(hours));
    }

    public ScheduledJobBuilder namePattern(String pattern) {
        return parameter("namePattern", pattern);
    }

    public ScheduledJobBuilder dryRun(boolean dryRun) {
        return parameter("dryRun", String.valueOf(dryRun));
    }

    public ScheduledJobBuilder safeMode(boolean safeMode) {
        return parameter("safeMode", String.valueOf(safeMode));
    }

    // VM Selector management
    public ScheduledJobBuilder vmSelector(String type, String value) {
        return vmSelector(type, value, null);
    }

    public ScheduledJobBuilder vmSelector(String type, String value, String excludeExpression) {
        String key = type + ":" + value;
        this.vmSelectors.put(key, new VMSelector(type, value, excludeExpression));
        return this;
    }

    // Common VM selectors
    public ScheduledJobBuilder selectAllVMs() {
        return vmSelector("ALL", "*");
    }

    public ScheduledJobBuilder selectByVMId(int vmId) {
        return vmSelector("VM_ID", String.valueOf(vmId));
    }

    public ScheduledJobBuilder selectByVMIds(int... vmIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vmIds.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vmIds[i]);
        }
        return vmSelector("VM_ID", sb.toString());
    }

    public ScheduledJobBuilder selectByTagExpression(String expression) {
        return vmSelector("TAG_EXPRESSION", expression);
    }

    public ScheduledJobBuilder selectByTagExpression(String expression, String excludeExpression) {
        return vmSelector("TAG_EXPRESSION", expression, excludeExpression);
    }

    public ScheduledJobBuilder selectByNamePattern(String pattern) {
        return vmSelector("NAME_PATTERN", pattern);
    }

    public ScheduledJobBuilder selectByNode(String node) {
        return vmSelector("NODE", node);
    }

    /**
     * Build the ScheduledJob entity with validation
     */
    public ScheduledJob build() {
        // Validation
        Objects.requireNonNull(name, "Job name is required");
        Objects.requireNonNull(taskType, "Task type is required");
        Objects.requireNonNull(cronExpression, "Cron expression is required");

        if (name.length() > 200) {
            throw new IllegalStateException("Job name cannot exceed 200 characters");
        }

        if (cronExpression.length() > 120) {
            throw new IllegalStateException("Cron expression cannot exceed 120 characters");
        }

        if (maxRetries < 0) {
            throw new IllegalStateException("Max retries must be non-negative");
        }

        if (retryDelaySeconds < 0) {
            throw new IllegalStateException("Retry delay must be non-negative");
        }

        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("Timeout must be positive");
        }

        // Create the entity
        ScheduledJob job = new ScheduledJob();
        job.name = name;
        job.description = description;
        job.taskType = taskType;
        job.cronExpression = cronExpression;
        job.enabled = enabled;
        job.maxRetries = maxRetries;
        job.retryDelaySeconds = retryDelaySeconds;
        job.timeoutSeconds = timeoutSeconds;
        job.createdBy = createdBy;
        job.updatedBy = updatedBy;
        job.createdAt = Instant.now();
        job.updatedAt = Instant.now();

        // Add parameters
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            job.addParameter(entry.getKey(), entry.getValue());
        }

        // Add VM selectors
        for (VMSelector selector : vmSelectors.values()) {
            job.addVMSelector(selector.type, selector.value, selector.excludeExpression);
        }

        return job;
    }

    // Inner class for VM selector data
    private static class VMSelector {
        final String type;
        final String value;
        final String excludeExpression;

        VMSelector(String type, String value, String excludeExpression) {
            this.type = type;
            this.value = value;
            this.excludeExpression = excludeExpression;
        }
    }
}
