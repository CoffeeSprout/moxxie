package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "VNet information")
public record VNetResponseDTO(
    @Schema(description = "VNet identifier", example = "client1-webapp")
    String vnetId,

    @Schema(description = "SDN zone", example = "localzone")
    String zone,

    @Schema(description = "VLAN tag", example = "101")
    Integer vlanTag,

    @Schema(description = "VNet alias/description", example = "client1")
    String alias,

    @Schema(description = "VNet type", example = "vnet")
    String type
) {}
