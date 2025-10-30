package com.coffeesprout.api.dto.cluster;

import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Specification for a group of similar nodes in a cluster")
public record NodeGroupSpec(
    @Schema(description = "Node group name", example = "control-plane", required = true)
    @NotBlank(message = "Node group name is required")
    @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
             message = "Node group name must be lowercase alphanumeric with optional hyphens")
    String name,

    @Schema(description = "Role of nodes in this group", required = true)
    @NotNull(message = "Node role is required") NodeRole role,

    @Schema(description = "Number of nodes to provision", example = "3", required = true)
    @NotNull(message = "Node count is required") @Min(value = 1, message = "At least one node is required")
    @Max(value = 100, message = "Cannot exceed 100 nodes per group")
    Integer count,

    @Schema(description = "VM template for nodes in this group", required = true)
    @NotNull(message = "Node template is required") @Valid
    NodeTemplate template,

    @Schema(description = "Node placement constraints")
    @Valid
    PlacementConstraints placement,

    @Schema(description = "Labels/tags to apply to nodes in this group")
    Set<String> tags,

    @Schema(description = "Node group specific metadata")
    Map<String, String> metadata
) {
    public NodeGroupSpec {
        if (placement == null) {
            placement = new PlacementConstraints(null, null, null, null);
        }
        if (tags == null) {
            tags = Set.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    @Schema(description = "Node roles in a cluster")
    public enum NodeRole {
        @Schema(description = "Control plane node (masters)")
        CONTROL_PLANE,

        @Schema(description = "Worker node")
        WORKER,

        @Schema(description = "Dedicated etcd node")
        ETCD,

        @Schema(description = "Combined control plane and worker")
        CONTROL_PLANE_WORKER,

        @Schema(description = "Gateway/Load balancer node")
        GATEWAY,

        @Schema(description = "Storage node")
        STORAGE,

        @Schema(description = "Bootstrap node (temporary, for OKD/OpenShift)")
        BOOTSTRAP,

        @Schema(description = "Bastion/installer host")
        BASTION
    }
}
