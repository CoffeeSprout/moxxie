package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Detailed VM status information")
public record VMStatusDetailResponse(
    @Schema(description = "VM ID", example = "100")
    int vmid,
    
    @Schema(description = "VM name", example = "test-vm")
    String name,
    
    @Schema(description = "VM status (running, stopped, suspended)", example = "running")
    String status,
    
    @Schema(description = "QMP status", example = "running")
    String qmpStatus,
    
    @Schema(description = "CPU usage percentage", example = "15.5")
    double cpuUsage,
    
    @Schema(description = "Number of CPUs", example = "4")
    int cpus,
    
    @Schema(description = "Memory usage in bytes", example = "2147483648")
    long memoryUsage,
    
    @Schema(description = "Maximum memory in bytes", example = "4294967296")
    long maxMemory,
    
    @Schema(description = "Memory usage percentage", example = "50.0")
    double memoryPercent,
    
    @Schema(description = "Disk read in bytes", example = "1073741824")
    long diskRead,
    
    @Schema(description = "Disk write in bytes", example = "536870912")
    long diskWrite,
    
    @Schema(description = "Disk usage in bytes", example = "10737418240")
    long diskUsage,
    
    @Schema(description = "Maximum disk size in bytes", example = "53687091200")
    long maxDisk,
    
    @Schema(description = "Network input in bytes", example = "104857600")
    long networkIn,
    
    @Schema(description = "Network output in bytes", example = "52428800")
    long networkOut,
    
    @Schema(description = "Uptime in seconds", example = "3600")
    long uptime,
    
    @Schema(description = "Process ID", example = "12345")
    String pid,
    
    @Schema(description = "Is VM running", example = "true")
    boolean running,
    
    @Schema(description = "Lock status (migrate, backup, snapshot, etc.)", example = "null")
    String lock,
    
    @Schema(description = "QEMU Guest Agent status (0=not running, 1=running)", example = "1")
    int agentStatus
) {}