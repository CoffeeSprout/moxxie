package com.coffeesprout.scheduler.event;

/**
 * Event fired when a new scheduled job is created and needs to be scheduled in Quartz.
 * This event is used to decouple the database transaction from Quartz scheduling operations.
 */
public class JobCreatedEvent {
    
    private final Long jobId;
    private final String jobName;
    private final boolean enabled;
    
    public JobCreatedEvent(Long jobId, String jobName, boolean enabled) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.enabled = enabled;
    }
    
    public Long getJobId() {
        return jobId;
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}