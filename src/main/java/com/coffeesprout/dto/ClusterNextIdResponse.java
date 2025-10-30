package com.coffeesprout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from Proxmox cluster/nextid API endpoint.
 * Contains the next available VM ID in the cluster.
 */
public record ClusterNextIdResponse(
    @JsonProperty("data")
    Integer data
) {}
