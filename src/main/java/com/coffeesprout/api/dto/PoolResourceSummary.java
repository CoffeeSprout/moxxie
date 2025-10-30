package com.coffeesprout.api.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Resource summary for a pool")
public record PoolResourceSummary(
    @Schema(description = "Pool name", example = "nixz")
    String poolName,

    @Schema(description = "Number of VMs in the pool", example = "15")
    int vmCount,

    @Schema(description = "Number of running VMs", example = "10")
    int runningVMs,

    @Schema(description = "Number of stopped VMs", example = "5")
    int stoppedVMs,

    @Schema(description = "Total allocated vCPUs", example = "60")
    int totalVcpus,

    @Schema(description = "Total vCPUs for running VMs", example = "40")
    int runningVcpus,

    @Schema(description = "Total allocated memory in bytes", example = "64424509440")
    long totalMemoryBytes,

    @Schema(description = "Human-readable total memory", example = "60 GB")
    String totalMemoryHuman,

    @Schema(description = "Memory allocated to running VMs in bytes", example = "42949672960")
    long runningMemoryBytes,

    @Schema(description = "Human-readable running memory", example = "40 GB")
    String runningMemoryHuman,

    @Schema(description = "Total allocated storage in bytes", example = "1099511627776")
    long totalStorageBytes,

    @Schema(description = "Human-readable total storage", example = "1 TB")
    String totalStorageHuman,

    @Schema(description = "List of VMs in this pool")
    List<VMSummary> vms
) {
    @Schema(description = "Summary of a VM in the pool")
    public record VMSummary(
        @Schema(description = "VM ID", example = "100")
        int vmid,

        @Schema(description = "VM name", example = "web-server-01")
        String name,

        @Schema(description = "Number of vCPUs", example = "4")
        int vcpus,

        @Schema(description = "Allocated memory in bytes", example = "8589934592")
        long memoryBytes,

        @Schema(description = "Total storage across all disks in bytes", example = "107374182400")
        long storageBytes,

        @Schema(description = "VM status", example = "running")
        String status,

        @Schema(description = "Node where VM is located", example = "hv5")
        String node
    ) {}
}
