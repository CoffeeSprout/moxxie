package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NodeStatusResponse(
    @JsonProperty("node_name") String nodeName,
    @JsonProperty("cpu_usage") double cpuUsage,
    @JsonProperty("memory") MemoryInfo memory,
    @JsonProperty("cpu_info") CpuInfo cpuInfo,
    @JsonProperty("load_average") double[] loadAverage
) {

    public record MemoryInfo(
        @JsonProperty("total") long total,
        @JsonProperty("used") long used,
        @JsonProperty("free") long free,
        @JsonProperty("usage_percentage") double usagePercentage
    ) {}

    public record CpuInfo(
        @JsonProperty("cpus") int cpus,
        @JsonProperty("cores") int cores,
        @JsonProperty("sockets") int sockets
    ) {}
}
