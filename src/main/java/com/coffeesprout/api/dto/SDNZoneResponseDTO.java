package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "SDN Zone information")
public record SDNZoneResponseDTO(
    @Schema(description = "Zone identifier", example = "localzone")
    String zone,

    @Schema(description = "Zone type", example = "simple")
    String type,

    @Schema(description = "IPAM configuration", example = "pve")
    String ipam,

    @Schema(description = "DNS server", example = "8.8.8.8")
    String dns,

    @Schema(description = "Number of nodes", example = "3")
    Integer nodes
) {}
