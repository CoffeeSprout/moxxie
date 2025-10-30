package com.coffeesprout.scheduler.api.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for job execution history
 */
@RegisterForReflection
public record JobExecutionResponse(
    @JsonProperty("id")
    Long id,

    @JsonProperty("executionId")
    String executionId,

    @JsonProperty("jobId")
    Long jobId,

    @JsonProperty("jobName")
    String jobName,

    @JsonProperty("status")
    String status,

    @JsonProperty("startedAt")
    Instant startedAt,

    @JsonProperty("completedAt")
    Instant completedAt,

    @JsonProperty("duration")
    Long duration,

    @JsonProperty("processedVMs")
    int processedVMs,

    @JsonProperty("successfulVMs")
    int successfulVMs,

    @JsonProperty("failedVMs")
    int failedVMs,

    @JsonProperty("errorMessage")
    String errorMessage,

    @JsonProperty("executionDetails")
    Map<String, Object> executionDetails,

    @JsonProperty("manualTrigger")
    boolean manualTrigger
) {

    /**
     * Create a response from a JobExecution entity
     */
    public static JobExecutionResponse from(com.coffeesprout.scheduler.entity.JobExecution execution) {
        Long duration = null;
        if (execution.startedAt != null && execution.completedAt != null) {
            duration = execution.completedAt.toEpochMilli() - execution.startedAt.toEpochMilli();
        }

        boolean isManual = execution.executionDetails != null &&
            Boolean.TRUE.equals(execution.executionDetails.get("manualTrigger"));

        return new JobExecutionResponse(
            execution.id,
            execution.executionId,
            execution.job.id,
            execution.job.name,
            execution.status,
            execution.startedAt,
            execution.completedAt,
            duration,
            execution.processedVMs,
            execution.successfulVMs,
            execution.failedVMs,
            execution.errorMessage,
            execution.executionDetails,
            isManual
        );
    }
}
