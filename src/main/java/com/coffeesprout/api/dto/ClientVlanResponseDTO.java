package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

@Schema(description = "Client VLAN allocation information")
public record ClientVlanResponseDTO(
    @Schema(description = "Client identifier", example = "client1")
    String clientId,
    
    @Schema(description = "Allocated VLAN tag", example = "101")
    Integer vlanTag,
    
    @Schema(description = "List of VNet IDs associated with this client", example = "[\"client1-webapp\", \"client1-api\"]")
    List<String> vnetIds
) {}