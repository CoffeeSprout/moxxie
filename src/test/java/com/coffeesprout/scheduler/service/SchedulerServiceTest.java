package com.coffeesprout.scheduler.service;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.entity.TaskType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SchedulerServiceTest {

    @Inject
    SchedulerService schedulerService;

    @Inject
    Scheduler scheduler;

    @Test
    @Transactional
    void testSchedulerInitialization() throws SchedulerException {
        // Verify scheduler is running
        assertNotNull(scheduler);
        assertTrue(scheduler.isStarted());
        assertFalse(scheduler.isShutdown());
    }

    @Test
    @Transactional
    void testCreateJob() throws SchedulerException {
        // Ensure test task type exists
        TaskType testType = TaskType.findByName("test");
        if (testType == null) {
            testType = new TaskType();
            testType.name = "test";
            testType.displayName = "Test Task";
            testType.description = "Test task for unit tests";
            testType.taskClass = "com.coffeesprout.scheduler.tasks.TestTask";
            testType.persist();
        }

        // Create a test job
        String jobName = "test-job-" + System.currentTimeMillis();
        ScheduledJob job = schedulerService.createJob(
            jobName,
            "Test job for unit test",
            "test",
            "0 0 12 * * ?", // Daily at noon
            "unit-test"
        );

        assertNotNull(job);
        assertNotNull(job.id);
        assertEquals(jobName, job.name);
        assertEquals("test", job.taskType.name);
        assertTrue(job.enabled);

        // NOTE: Job scheduling in Quartz now happens asynchronously via events
        // after transaction commit, so we can't verify it's scheduled immediately
    }

    @Test
    @Transactional
    void testDisableJob() throws SchedulerException {
        // Create a job first
        TaskType testType = TaskType.findByName("test");
        if (testType == null) {
            testType = new TaskType();
            testType.name = "test";
            testType.displayName = "Test Task";
            testType.description = "Test task for unit tests";
            testType.taskClass = "com.coffeesprout.scheduler.tasks.TestTask";
            testType.persist();
        }

        String jobName = "disable-test-job-" + System.currentTimeMillis();
        ScheduledJob job = schedulerService.createJob(
            jobName,
            "Test job to disable",
            "test",
            "0 0 12 * * ?",
            "unit-test"
        );

        // Manually schedule the job for testing (since async scheduling is disabled in tests)
        schedulerService.scheduleJob(job);

        // Verify job is scheduled
        assertTrue(scheduler.checkExists(org.quartz.JobKey.jobKey(jobName, "moxxie-jobs")));

        // Disable the job
        schedulerService.setJobEnabled(job.id, false);

        // Verify job is disabled in database
        job = ScheduledJob.findById(job.id);
        assertFalse(job.enabled);

        // Verify job is removed from Quartz
        assertFalse(scheduler.checkExists(org.quartz.JobKey.jobKey(jobName, "moxxie-jobs")));
    }
}
