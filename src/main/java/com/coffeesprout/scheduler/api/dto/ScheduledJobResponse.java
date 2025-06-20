package com.coffeesprout.scheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for scheduled job details
 */
@RegisterForReflection
public record ScheduledJobResponse(
    @JsonProperty("id")
    Long id,
    
    @JsonProperty("name")
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("taskType")
    String taskType,
    
    @JsonProperty("taskDisplayName")
    String taskDisplayName,
    
    @JsonProperty("cronExpression")
    String cronExpression,
    
    @JsonProperty("enabled")
    boolean enabled,
    
    @JsonProperty("maxRetries")
    int maxRetries,
    
    @JsonProperty("retryDelaySeconds")
    int retryDelaySeconds,
    
    @JsonProperty("timeoutSeconds")
    int timeoutSeconds,
    
    @JsonProperty("parameters")
    Map<String, String> parameters,
    
    @JsonProperty("vmSelectors")
    List<VMSelectorResponse> vmSelectors,
    
    @JsonProperty("createdAt")
    Instant createdAt,
    
    @JsonProperty("updatedAt")
    Instant updatedAt,
    
    @JsonProperty("createdBy")
    String createdBy,
    
    @JsonProperty("updatedBy")
    String updatedBy,
    
    @JsonProperty("nextFireTime")
    Date nextFireTime,
    
    @JsonProperty("lastExecutionStatus")
    String lastExecutionStatus,
    
    @JsonProperty("lastExecutionTime")
    Instant lastExecutionTime
) {
    
    /**
     * VM selector configuration response
     */
    @RegisterForReflection
    public record VMSelectorResponse(
        @JsonProperty("id")
        Long id,
        
        @JsonProperty("type")
        String type,
        
        @JsonProperty("value")
        String value,
        
        @JsonProperty("excludeExpression")
        String excludeExpression
    ) {}
}