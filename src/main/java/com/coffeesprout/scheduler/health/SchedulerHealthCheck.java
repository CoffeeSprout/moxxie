package com.coffeesprout.scheduler.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health check for the Quartz scheduler
 */
@ApplicationScoped
@Readiness
public class SchedulerHealthCheck implements HealthCheck {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerHealthCheck.class);
    
    @Inject
    Scheduler scheduler;
    
    @Override
    public HealthCheckResponse call() {
        try {
            boolean started = scheduler.isStarted();
            boolean shutdown = scheduler.isShutdown();
            boolean standby = scheduler.isInStandbyMode();
            int runningJobs = scheduler.getCurrentlyExecutingJobs().size();
            
            // Get metadata for additional information
            SchedulerMetaData metaData = scheduler.getMetaData();
            
            HealthCheckResponse response;
            
            if (started && !shutdown && !standby) {
                response = HealthCheckResponse.named("scheduler")
                    .up()
                    .withData("started", started)
                    .withData("shutdown", shutdown)
                    .withData("standby", standby)
                    .withData("runningJobs", runningJobs)
                    .withData("schedulerName", metaData.getSchedulerName())
                    .withData("schedulerInstanceId", metaData.getSchedulerInstanceId())
                    .withData("jobStoreClass", metaData.getJobStoreClass().getSimpleName())
                    .withData("threadPoolSize", metaData.getThreadPoolSize())
                    .withData("jobsExecuted", metaData.getNumberOfJobsExecuted())
                    .withData("clustered", metaData.isJobStoreClustered())
                    .build();
            } else {
                response = HealthCheckResponse.named("scheduler")
                    .down()
                    .withData("started", started)
                    .withData("shutdown", shutdown)
                    .withData("standby", standby)
                    .build();
            }
            
            return response;
            
        } catch (Exception e) {
            LOG.error("Scheduler health check failed", e);
            return HealthCheckResponse.named("scheduler")
                .down()
                .withData("error", e.getMessage())
                .withData("errorType", e.getClass().getSimpleName())
                .build();
        }
    }
}