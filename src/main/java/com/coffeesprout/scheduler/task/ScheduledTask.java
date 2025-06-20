package com.coffeesprout.scheduler.task;

/**
 * Interface for all scheduled tasks
 */
public interface ScheduledTask {
    
    /**
     * Execute the scheduled task
     * 
     * @param context The task execution context
     * @return The result of the task execution
     */
    TaskResult execute(TaskContext context);
    
    /**
     * Get task type identifier
     * 
     * @return The task type name
     */
    String getTaskType();
    
    /**
     * Validate task configuration
     * 
     * @param context The task context to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    default void validateConfiguration(TaskContext context) {
        // Default implementation - subclasses can override
    }
}