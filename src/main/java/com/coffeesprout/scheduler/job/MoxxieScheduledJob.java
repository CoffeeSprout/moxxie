package com.coffeesprout.scheduler.job;

import com.coffeesprout.scheduler.entity.JobExecution;
import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.task.ScheduledTask;
import com.coffeesprout.scheduler.task.TaskContext;
import com.coffeesprout.scheduler.task.TaskResult;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quartz job implementation that bridges to our ScheduledTask system
 */
public class MoxxieScheduledJob implements Job {
    
    private static final Logger log = LoggerFactory.getLogger(MoxxieScheduledJob.class);
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long jobId = Long.parseLong(context.getJobDetail().getJobDataMap().getString("jobId"));
        String jobName = context.getJobDetail().getJobDataMap().getString("jobName");
        String executionId = context.getMergedJobDataMap().getString("executionId");
        boolean isManualTrigger = context.getMergedJobDataMap().getBoolean("manualTrigger");
        
        if (executionId == null) {
            executionId = UUID.randomUUID().toString();
        }
        
        log.info("Starting execution {} for job {} (manual: {})", executionId, jobName, isManualTrigger);
        
        // Get CDI container to access services
        try {
            // Execute in transaction
            Arc.container().select(JobExecutor.class).get().executeJob(jobId, executionId, isManualTrigger);
        } catch (Exception e) {
            log.error("Failed to execute job {}: {}", jobName, e.getMessage(), e);
            throw new JobExecutionException("Job execution failed", e);
        }
    }
    
    /**
     * Helper class to handle transactional job execution
     */
    @ApplicationScoped
    @io.quarkus.arc.Unremovable
    public static class JobExecutor {
        
        private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);
        
        @Transactional
        public void executeJob(Long jobId, String executionId, boolean isManualTrigger) {
            // Note: Transaction timeout would need to be configured at the datasource level
            // or use programmatic transaction management for timeout control
            JobExecution execution = null;
            
            try {
                // Load job from database
                ScheduledJob job = ScheduledJob.findById(jobId);
                if (job == null) {
                    log.error("Job not found with ID: {}", jobId);
                    return;
                }
                
                // Create execution record
                execution = new JobExecution();
                execution.job = job;
                execution.executionId = executionId;
                execution.status = JobExecution.Status.RUNNING.getValue();
                execution.startedAt = Instant.now();
                execution.executionDetails = new HashMap<>();
                execution.executionDetails.put("manualTrigger", isManualTrigger);
                execution.persist();
                
                log.info("Created execution record {} for job {}", executionId, job.name);
                
                // Load task implementation
                Class<?> taskClass = Class.forName(job.taskType.taskClass);
                Instance<?> taskInstance = Arc.container().select(taskClass);
                
                if (!taskInstance.isResolvable()) {
                    throw new IllegalStateException("Task implementation not found: " + job.taskType.taskClass);
                }
                
                ScheduledTask task = (ScheduledTask) taskInstance.get();
                
                // Create task context
                TaskContext taskContext = new TaskContext();
                taskContext.setJob(job);
                taskContext.setExecution(execution);
                taskContext.setManualTrigger(isManualTrigger);
                
                // Add job parameters to context
                for (var param : job.parameters) {
                    taskContext.addParameter(param.paramKey, param.paramValue);
                }
                
                // Execute the task
                log.info("Executing task {} for job {}", job.taskType.name, job.name);
                TaskResult result = task.execute(taskContext);
                
                // Update execution record with results
                execution.processedVMs = result.getProcessedCount();
                execution.successfulVMs = result.getSuccessCount();
                execution.failedVMs = result.getFailedCount();
                
                if (result.isSuccess()) {
                    execution.complete();
                    log.info("Job {} completed successfully. Processed: {}, Success: {}, Failed: {}", 
                            job.name, result.getProcessedCount(), result.getSuccessCount(), result.getFailedCount());
                } else {
                    execution.fail(result.getErrorMessage());
                    log.error("Job {} failed: {}", job.name, result.getErrorMessage());
                }
                
                // Store additional details
                Map<String, Object> details = execution.executionDetails;
                details.put("processedVMs", result.getProcessedCount());
                details.put("successfulVMs", result.getSuccessCount());
                details.put("failedVMs", result.getFailedCount());
                if (result.getDetails() != null) {
                    details.putAll(result.getDetails());
                }
                
                execution.persist();
                
            } catch (Exception e) {
                log.error("Job execution failed with exception: {}", e.getMessage(), e);
                
                if (execution != null) {
                    execution.fail("Execution failed: " + e.getMessage());
                    execution.persist();
                }
                
                throw new RuntimeException("Job execution failed", e);
            }
        }
    }
}