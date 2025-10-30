package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InfoResponse(
    @JsonProperty("instance_id") String instanceId,
    @JsonProperty("location") String location,
    @JsonProperty("version") String version,
    @JsonProperty("cluster_endpoint") String clusterEndpoint,
    @JsonProperty("health_status") String healthStatus
) {}
