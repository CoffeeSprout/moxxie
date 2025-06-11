package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

@Schema(description = "VLAN assignment information")
public record VlanAssignmentDTO(
    @Schema(description = "VLAN tag", example = "101")
    Integer vlanTag,
    
    @Schema(description = "Client identifier (if assigned)", example = "client1")
    String clientId,
    
    @Schema(description = "List of SDN VNets using this VLAN", example = "[\"client1-webapp\", \"client1-api\"]")
    List<String> vnetIds,
    
    @Schema(description = "Status of the VLAN", example = "allocated")
    String status,
    
    @Schema(description = "Description or notes", example = "Production client infrastructure")
    String description
) {}