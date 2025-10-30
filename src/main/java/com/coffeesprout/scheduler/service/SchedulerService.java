package com.coffeesprout.scheduler.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.coffeesprout.scheduler.entity.JobExecution;
import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.entity.ScheduledJobBuilder;
import com.coffeesprout.scheduler.entity.TaskType;
import com.coffeesprout.scheduler.job.MoxxieScheduledJob;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Startup
public class SchedulerService {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);
    private static final String JOB_GROUP = "moxxie-jobs";
    private static final String TRIGGER_GROUP = "moxxie-triggers";

    @Inject
    Scheduler scheduler;

    /**
     * Initialize scheduler on startup
     */
    @Transactional
    void onStart(@Observes StartupEvent event) {
        LOG.info("Initializing Moxxie Scheduler Service");
        try {
            // Load all enabled jobs from database
            List<ScheduledJob> enabledJobs = ScheduledJob.findEnabled();
            LOG.info("Found {} enabled scheduled jobs", enabledJobs.size());

            for (ScheduledJob job : enabledJobs) {
                try {
                    scheduleJob(job);
                } catch (Exception e) {
                    LOG.error("Failed to schedule job {}: {}", job.name, e.getMessage(), e);
                }
            }

            LOG.info("Scheduler initialization completed");
        } catch (Exception e) {
            LOG.error("Failed to initialize scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Schedule a job in Quartz
     */
    public void scheduleJob(ScheduledJob job) throws SchedulerException {
        if (!job.enabled) {
            LOG.debug("Job {} is disabled, skipping scheduling", job.name);
            return;
        }

        JobKey jobKey = new JobKey(job.name, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(job.name, TRIGGER_GROUP);

        // Check if job already exists
        if (scheduler.checkExists(jobKey)) {
            LOG.debug("Job {} already scheduled, updating", job.name);
            unscheduleJob(job.name);
        }

        // Create Quartz job
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobId", String.valueOf(job.id));
        jobDataMap.put("jobName", job.name);

        JobDetail jobDetail = JobBuilder.newJob(MoxxieScheduledJob.class)
            .withIdentity(jobKey)
            .withDescription(job.description)
            .usingJobData(jobDataMap)
            .storeDurably(false)
            .build();

        // Create trigger with cron expression
        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .withSchedule(CronScheduleBuilder.cronSchedule(job.cronExpression)
                .withMisfireHandlingInstructionFireAndProceed())
            .build();

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
        LOG.info("Scheduled job {} with cron expression: {}", job.name, job.cronExpression);
    }

    /**
     * Unschedule a job from Quartz
     */
    public void unscheduleJob(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            LOG.info("Unscheduled job: {}", jobName);
        }
    }

    /**
     * Pause a scheduled job
     */
    public void pauseJob(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);
        scheduler.pauseJob(jobKey);
        LOG.info("Paused job: {}", jobName);
    }

    /**
     * Resume a paused job
     */
    public void resumeJob(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);
        scheduler.resumeJob(jobKey);
        LOG.info("Resumed job: {}", jobName);
    }

    /**
     * Trigger a job immediately
     */
    @Transactional
    public String triggerJobNow(String jobName) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, JOB_GROUP);

        if (!scheduler.checkExists(jobKey)) {
            throw new IllegalArgumentException("Job not found: " + jobName);
        }

        // Generate unique execution ID
        String executionId = UUID.randomUUID().toString();

        // Create job data for this execution
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("manualTrigger", "true");
        dataMap.put("executionId", executionId);

        // Trigger the job
        scheduler.triggerJob(jobKey, dataMap);
        LOG.info("Manually triggered job {} with execution ID: {}", jobName, executionId);

        return executionId;
    }

    /**
     * Get next scheduled execution time for a job
     */
    public Date getNextFireTime(String jobName) throws SchedulerException {
        TriggerKey triggerKey = new TriggerKey(jobName, TRIGGER_GROUP);
        Trigger trigger = scheduler.getTrigger(triggerKey);

        if (trigger != null) {
            return trigger.getNextFireTime();
        }
        return null;
    }

    /**
     * Update job schedule
     */
    @Transactional
    public void updateJobSchedule(Long jobId, String newCronExpression) throws SchedulerException {
        ScheduledJob job = ScheduledJob.findById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found with ID: " + jobId);
        }

        // Validate cron expression
        try {
            CronExpression.validateExpression(newCronExpression);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + newCronExpression, e);
        }

        // Update database
        job.cronExpression = newCronExpression;
        job.updatedAt = Instant.now();
        job.persist();

        // Update Quartz schedule if job is enabled
        if (job.enabled) {
            unscheduleJob(job.name);
            scheduleJob(job);
        }

        LOG.info("Updated schedule for job {} to: {}", job.name, newCronExpression);
    }

    /**
     * Enable or disable a job
     */
    @Transactional
    public void setJobEnabled(Long jobId, boolean enabled) throws SchedulerException {
        ScheduledJob job = ScheduledJob.findById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found with ID: " + jobId);
        }

        job.enabled = enabled;
        job.updatedAt = Instant.now();
        job.persist();

        if (enabled) {
            scheduleJob(job);
        } else {
            unscheduleJob(job.name);
        }

        LOG.info("{} job: {}", enabled ? "Enabled" : "Disabled", job.name);
    }

    /**
     * Create a new scheduled job
     */
    @Transactional
    public ScheduledJob createJob(String name, String description, String taskTypeName,
                                  String cronExpression, String createdBy) throws SchedulerException {
        // Validate inputs
        if (ScheduledJob.findByName(name) != null) {
            throw new IllegalArgumentException("Job with name already exists: " + name);
        }

        TaskType taskType = TaskType.findByName(taskTypeName);
        if (taskType == null) {
            throw new IllegalArgumentException("Invalid task type: " + taskTypeName);
        }

        // Validate cron expression
        try {
            CronExpression.validateExpression(cronExpression);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
        }

        // Create job entity using builder
        ScheduledJob job = ScheduledJobBuilder.builder()
            .name(name)
            .description(description)
            .taskType(taskType)
            .cronExpression(cronExpression)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
        job.persist();

        // NOTE: Scheduling is now handled by JobCreatedEvent to avoid transaction conflicts
        // The job will be scheduled after the transaction commits

        LOG.info("Created new scheduled job: {}", name);
        return job;
    }

    /**
     * Delete a scheduled job
     */
    @Transactional
    public void deleteJob(Long jobId) throws SchedulerException {
        ScheduledJob job = ScheduledJob.findById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found with ID: " + jobId);
        }

        // Unschedule from Quartz
        unscheduleJob(job.name);

        // Delete from database
        job.delete();

        LOG.info("Deleted scheduled job: {}", job.name);
    }

    /**
     * Clean up old job executions periodically
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    void cleanupOldExecutions() {
        LOG.info("Starting cleanup of old job executions");

        try {
            // Delete executions older than 30 days
            Instant cutoffDate = Instant.now().minusSeconds(30 * 24 * 60 * 60);
            long deleted = JobExecution.delete("completedAt < ?1", cutoffDate);

            LOG.info("Deleted {} old job executions", deleted);
        } catch (Exception e) {
            LOG.error("Failed to cleanup old executions: {}", e.getMessage(), e);
        }
    }
}
