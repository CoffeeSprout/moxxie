package com.coffeesprout.scheduler.event;

import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.service.SchedulerService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles scheduler-related events after transaction completion.
 * This ensures Quartz operations don't interfere with JTA transactions.
 */
@ApplicationScoped
@Unremovable
public class SchedulerEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(SchedulerEventHandler.class);
    
    @Inject
    SchedulerService schedulerService;
    
    /**
     * Handle job creation events after the transaction successfully commits.
     * This prevents transaction conflicts between JTA and Quartz's jdbc-cmt store.
     */
    public void onJobCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) JobCreatedEvent event) {
        log.debug("Handling JobCreatedEvent for job {} after transaction commit", event.getJobName());
        
        if (!event.isEnabled()) {
            log.debug("Job {} is disabled, skipping Quartz scheduling", event.getJobName());
            return;
        }
        
        try {
            // Load the job in a new transaction context
            scheduleJobInNewTransaction(event.getJobId());
        } catch (Exception e) {
            log.error("Failed to schedule job {} in Quartz after creation: {}", 
                     event.getJobName(), e.getMessage(), e);
            // Note: The job is still created in the database, just not scheduled
            // This can be handled by a background reconciliation process
        }
    }
    
    /**
     * Schedule the job in a new transaction to avoid conflicts
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void scheduleJobInNewTransaction(Long jobId) {
        try {
            ScheduledJob job = ScheduledJob.findById(jobId);
            if (job == null) {
                log.error("Job not found with ID {} when trying to schedule", jobId);
                return;
            }
            
            if (job.enabled) {
                schedulerService.scheduleJob(job);
                log.info("Successfully scheduled job {} in Quartz", job.name);
            }
        } catch (Exception e) {
            log.error("Failed to schedule job with ID {}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to schedule job", e);
        }
    }
}