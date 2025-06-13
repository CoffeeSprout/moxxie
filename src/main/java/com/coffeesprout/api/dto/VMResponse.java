package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Virtual Machine response")
public record VMResponse(
    @Schema(description = "VM ID", example = "101")
    int vmid,
    
    @Schema(description = "VM name", example = "web-server-01")
    String name,
    
    @Schema(description = "Node where VM is located", example = "pve1")
    String node,
    
    @Schema(description = "VM status", example = "running")
    String status,
    
    @Schema(description = "Number of CPU cores", example = "4")
    int cpus,
    
    @Schema(description = "Maximum memory in bytes", example = "8589934592")
    long maxmem,
    
    @Schema(description = "Maximum disk size in bytes", example = "107374182400")
    long maxdisk,
    
    @Schema(description = "Uptime in seconds", example = "3600")
    long uptime,
    
    @Schema(description = "VM type (qemu or lxc)", example = "qemu")
    String type,
    
    @Schema(description = "VM tags", example = "[\"moxxie\", \"client:nixz\", \"env:prod\"]")
    java.util.List<String> tags,
    
    @Schema(description = "Resource pool the VM belongs to", example = "moxxie-pool")
    String pool
) {}