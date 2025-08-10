package com.coffeesprout.scheduler.tasks;

import com.coffeesprout.scheduler.task.ScheduledTask;
import com.coffeesprout.scheduler.task.TaskContext;
import com.coffeesprout.scheduler.task.TaskResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Simple test task to verify scheduler infrastructure
 */
@ApplicationScoped
public class TestTask implements ScheduledTask {
    
    private static final Logger LOG = LoggerFactory.getLogger(TestTask.class);
    
    @Override
    public TaskResult execute(TaskContext context) {
        LOG.info("Executing test task for job: {}", context.getJob().name);
        
        try {
            // Simulate some work
            String message = context.getParameter("message", "Hello from test task!");
            int delay = context.getIntParameter("delay", 1000);
            
            LOG.info("Test task message: {}", message);
            
            if (delay > 0) {
                LOG.info("Simulating work for {} ms...", delay);
                Thread.sleep(delay);
            }
            
            // Return success
            return TaskResult.success()
                .withProcessedCount(1)
                .withSuccessCount(1)
                .withDetail("message", message)
                .withDetail("executedAt", Instant.now().toString())
                .withDetail("jobName", context.getJob().name)
                .withDetail("manualTrigger", context.isManualTrigger());
                
        } catch (Exception e) {
            LOG.error("Test task failed: {}", e.getMessage(), e);
            return TaskResult.failure("Test task failed: " + e.getMessage())
                .withProcessedCount(1)
                .withFailedCount(1);
        }
    }
    
    @Override
    public String getTaskType() {
        return "test";
    }
}