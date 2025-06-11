package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to create a new VNet for a client/project")
public record CreateVNetRequestDTO(
    @Schema(description = "Client identifier", required = true, example = "client1")
    String clientId,
    
    @Schema(description = "Project name", example = "web-app")
    String project,
    
    @Schema(description = "SDN zone to create VNet in", example = "localzone")
    String zone,
    
    @Schema(description = "VLAN tag to assign", required = true, example = "101")
    Integer vlanTag
) {}