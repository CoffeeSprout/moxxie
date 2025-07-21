package com.coffeesprout.api.dto.cluster;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Specification for provisioning a complete cluster")
public record ClusterSpec(
    @Schema(description = "Unique cluster name", example = "talos-prod-01", required = true)
    @NotBlank(message = "Cluster name is required")
    @Size(max = 63, message = "Cluster name must not exceed 63 characters")
    @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", 
             message = "Cluster name must be lowercase alphanumeric with optional hyphens")
    String name,
    
    @Schema(description = "Type of cluster to provision", example = "TALOS", required = true)
    @NotNull(message = "Cluster type is required")
    ClusterType type,
    
    @Schema(description = "Node group specifications", required = true)
    @NotNull(message = "At least one node group is required")
    @Size(min = 1, message = "At least one node group is required")
    @Valid
    List<NodeGroupSpec> nodeGroups,
    
    @Schema(description = "Network topology configuration")
    @Valid
    NetworkTopology networkTopology,
    
    @Schema(description = "Global cloud-init configuration applied to all nodes")
    @Valid
    GlobalCloudInit globalCloudInit,
    
    @Schema(description = "Cluster metadata and labels")
    Map<String, String> metadata,
    
    @Schema(description = "Cluster provisioning options")
    @Valid
    ProvisioningOptions options
) {
    public ClusterSpec {
        if (metadata == null) {
            metadata = Map.of();
        }
        if (options == null) {
            options = new ProvisioningOptions(null, null, null, null);
        }
    }
    
    @Schema(description = "Supported cluster types")
    public enum ClusterType {
        @Schema(description = "Talos Linux Kubernetes cluster")
        TALOS,
        
        @Schema(description = "K3s lightweight Kubernetes")
        K3S,
        
        @Schema(description = "Generic VM cluster")
        GENERIC
    }
}