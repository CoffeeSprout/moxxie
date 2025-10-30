package com.coffeesprout.api.dto;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Detailed Virtual Machine response with network configuration")
public record VMDetailResponse(
    @Schema(description = "VM ID", example = "8200")
    int vmid,

    @Schema(description = "VM name", example = "web-server-01")
    String name,

    @Schema(description = "Node where VM is located", example = "hv6")
    String node,

    @Schema(description = "VM status", example = "running")
    String status,

    @Schema(description = "Number of CPU cores", example = "4")
    int cpus,

    @Schema(description = "Maximum memory in bytes", example = "8589934592")
    long maxmem,

    @Schema(description = "Maximum disk size in bytes", example = "107374182400")
    long maxdisk,

    @Schema(description = "Total disk size across all disks in bytes", example = "214748364800")
    long totalDiskSize,

    @Schema(description = "List of all disks attached to the VM")
    List<DiskInfo> disks,

    @Schema(description = "Uptime in seconds", example = "3600")
    long uptime,

    @Schema(description = "VM type (qemu or lxc)", example = "qemu")
    String type,

    @Schema(description = "Network interfaces")
    List<NetworkInterfaceInfo> networkInterfaces,

    @Schema(description = "Tags associated with the VM", example = "[\"moxxie\", \"production\"]")
    List<String> tags,

    @Schema(description = "Raw VM configuration (if available)")
    Map<String, Object> config
) {
    @Schema(description = "Network interface information")
    public record NetworkInterfaceInfo(
        @Schema(description = "Interface name", example = "net0")
        String name,

        @Schema(description = "MAC address", example = "BC:24:11:5E:7D:2C")
        String macAddress,

        @Schema(description = "Bridge", example = "vmbr0")
        String bridge,

        @Schema(description = "VLAN tag", example = "101")
        Integer vlan,

        @Schema(description = "Model", example = "virtio")
        String model,

        @Schema(description = "Firewall enabled", example = "false")
        boolean firewall,

        @Schema(description = "Raw configuration string", example = "virtio=BC:24:11:5E:7D:2C,bridge=vmbr0,tag=101")
        String rawConfig
    ) {}
}
