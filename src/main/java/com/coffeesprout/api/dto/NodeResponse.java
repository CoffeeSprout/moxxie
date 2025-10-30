package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NodeResponse(
    @JsonProperty("name") String name,
    @JsonProperty("cpu") int cpu,
    @JsonProperty("max_memory") long maxMemory,
    @JsonProperty("status") String status,
    @JsonProperty("uptime") long uptime
) {}
