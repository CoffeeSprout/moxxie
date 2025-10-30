package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Network interface information")
public record NetworkInterfaceResponse(
    @Schema(description = "Interface name", example = "vmbr0")
    String iface,

    @Schema(description = "Interface type (bridge, bond, eth, vlan)", example = "bridge")
    String type,

    @Schema(description = "Configuration method (static, manual)", example = "static")
    String method,

    @Schema(description = "IP address", example = "10.0.0.1")
    String address,

    @Schema(description = "Network mask", example = "255.255.255.0")
    String netmask,

    @Schema(description = "Gateway address", example = "10.0.0.254")
    String gateway,

    @Schema(description = "Bridge ports (for bridge interfaces)", example = "eno1")
    String bridgePorts,

    @Schema(description = "VLAN aware bridge", example = "true")
    boolean vlanAware,

    @Schema(description = "CIDR notation", example = "10.0.0.1/24")
    String cidr,

    @Schema(description = "Interface comments", example = "Management network")
    String comments,

    @Schema(description = "Interface is active", example = "true")
    boolean active,

    @Schema(description = "Auto-start on boot", example = "true")
    boolean autostart,

    @Schema(description = "Node where this interface exists", example = "pve1")
    String node
) {}
