package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NodeResourcesResponse(
    @JsonProperty("node_name") String nodeName,
    @JsonProperty("vms") List<VMSummary> vms,
    @JsonProperty("storage") List<StorageSummary> storage,
    @JsonProperty("total_vms") int totalVms,
    @JsonProperty("total_storage_pools") int totalStoragePools
) {
    
    public record VMSummary(
        @JsonProperty("vmid") int vmid,
        @JsonProperty("name") String name,
        @JsonProperty("status") String status,
        @JsonProperty("cpu") int cpu,
        @JsonProperty("memory") long memory,
        @JsonProperty("disk") long disk
    ) {}
    
    public record StorageSummary(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("total") long total,
        @JsonProperty("used") long used,
        @JsonProperty("free") long free,
        @JsonProperty("active") boolean active
    ) {}
}