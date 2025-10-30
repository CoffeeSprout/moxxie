package com.coffeesprout.scheduler.api.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for creating or updating a scheduled job
 */
@RegisterForReflection
public record ScheduledJobRequest(
    @NotBlank(message = "Job name is required")
    @JsonProperty("name")
    String name,

    @JsonProperty("description")
    String description,

    @NotBlank(message = "Task type is required")
    @JsonProperty("taskType")
    String taskType,

    @NotBlank(message = "Cron expression is required")
    @JsonProperty("cronExpression")
    String cronExpression,

    @JsonProperty("enabled")
    Boolean enabled,

    @JsonProperty("maxRetries")
    Integer maxRetries,

    @JsonProperty("retryDelaySeconds")
    Integer retryDelaySeconds,

    @JsonProperty("timeoutSeconds")
    Integer timeoutSeconds,

    @JsonProperty("parameters")
    Map<String, String> parameters,

    @JsonProperty("vmSelectors")
    List<VMSelectorRequest> vmSelectors
) {

    /**
     * VM selector configuration
     */
    @RegisterForReflection
    public record VMSelectorRequest(
        @NotNull(message = "Selector type is required") @JsonProperty("type")
        String type,

        @NotNull(message = "Selector value is required") @JsonProperty("value")
        String value,

        @JsonProperty("excludeExpression")
        String excludeExpression
    ) {}
}
