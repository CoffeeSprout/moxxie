package com.coffeesprout.api.dto.cluster;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Network topology configuration for the cluster")
public record NetworkTopology(
    @Schema(description = "Primary network bridge", example = "vmbr0", defaultValue = "vmbr0")
    String primaryBridge,
    
    @Schema(description = "Cluster network VLAN (for inter-node communication)", example = "100")
    @Min(value = 1, message = "VLAN must be between 1 and 4094")
    @Max(value = 4094, message = "VLAN must be between 1 and 4094")
    Integer clusterVlan,
    
    @Schema(description = "Public/external network VLAN", example = "200")
    @Min(value = 1, message = "VLAN must be between 1 and 4094")
    @Max(value = 4094, message = "VLAN must be between 1 and 4094")
    Integer publicVlan,
    
    @Schema(description = "Storage network VLAN", example = "300")
    @Min(value = 1, message = "VLAN must be between 1 and 4094")
    @Max(value = 4094, message = "VLAN must be between 1 and 4094")
    Integer storageVlan,
    
    @Schema(description = "Management network VLAN", example = "400")
    @Min(value = 1, message = "VLAN must be between 1 and 4094")
    @Max(value = 4094, message = "VLAN must be between 1 and 4094")
    Integer managementVlan,
    
    @Schema(description = "Custom network mappings by role")
    Map<String, NetworkMapping> roleNetworkMappings,
    
    @Schema(description = "Enable network isolation between node groups", defaultValue = "false")
    Boolean enableIsolation
) {
    public NetworkTopology {
        if (primaryBridge == null) {
            primaryBridge = "vmbr0";
        }
        if (roleNetworkMappings == null) {
            roleNetworkMappings = Map.of();
        }
        if (enableIsolation == null) {
            enableIsolation = false;
        }
    }
    
    @Schema(description = "Custom network mapping for specific roles")
    public record NetworkMapping(
        @Schema(description = "Network bridge override")
        String bridge,
        
        @Schema(description = "VLAN override")
        Integer vlan,
        
        @Schema(description = "Additional network interfaces")
        Map<String, String> additionalInterfaces
    ) {}
}