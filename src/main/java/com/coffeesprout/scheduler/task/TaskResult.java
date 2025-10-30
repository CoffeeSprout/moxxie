package com.coffeesprout.scheduler.task;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of a scheduled task execution
 */
public class TaskResult {

    private boolean success;
    private String errorMessage;
    private int processedCount;
    private int successCount;
    private int failedCount;
    private Map<String, Object> details = new HashMap<>();

    private TaskResult(boolean success) {
        this.success = success;
    }

    public static TaskResult success() {
        return new TaskResult(true);
    }

    public static TaskResult failure(String errorMessage) {
        TaskResult result = new TaskResult(false);
        result.errorMessage = errorMessage;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public TaskResult withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public TaskResult withProcessedCount(int processedCount) {
        this.processedCount = processedCount;
        return this;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public TaskResult withSuccessCount(int successCount) {
        this.successCount = successCount;
        return this;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public TaskResult withFailedCount(int failedCount) {
        this.failedCount = failedCount;
        return this;
    }

    public TaskResult withCounts(int processed, int success, int failed) {
        this.processedCount = processed;
        this.successCount = success;
        this.failedCount = failed;
        return this;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public TaskResult withDetail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public TaskResult withDetails(Map<String, Object> details) {
        this.details.putAll(details);
        return this;
    }

    @Override
    public String toString() {
        return "TaskResult{" +
                "success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", processedCount=" + processedCount +
                ", successCount=" + successCount +
                ", failedCount=" + failedCount +
                ", details=" + details +
                '}';
    }
}
