package com.coffeesprout.api.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Client VLAN allocation information")
public record ClientVlanResponseDTO(
    @Schema(description = "Client identifier", example = "client1")
    String clientId,

    @Schema(description = "Allocated VLAN tag", example = "101")
    Integer vlanTag,

    @Schema(description = "List of VNet IDs associated with this client", example = "[\"client1-webapp\", \"client1-api\"]")
    List<String> vnetIds
) {}
