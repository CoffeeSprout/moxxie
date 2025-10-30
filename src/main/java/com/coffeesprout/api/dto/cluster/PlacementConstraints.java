package com.coffeesprout.api.dto.cluster;

import java.util.Set;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Node placement constraints and anti-affinity rules")
public record PlacementConstraints(
    @Schema(description = "Anti-affinity strategy", defaultValue = "SOFT")
    AntiAffinityStrategy antiAffinity,

    @Schema(description = "Preferred nodes for placement")
    Set<String> preferredNodes,

    @Schema(description = "Nodes to avoid for placement")
    Set<String> avoidNodes,

    @Schema(description = "Required node labels/tags for placement")
    Set<String> requiredNodeTags
) {
    public PlacementConstraints {
        if (antiAffinity == null) {
            antiAffinity = AntiAffinityStrategy.SOFT;
        }
        if (preferredNodes == null) {
            preferredNodes = Set.of();
        }
        if (avoidNodes == null) {
            avoidNodes = Set.of();
        }
        if (requiredNodeTags == null) {
            requiredNodeTags = Set.of();
        }
    }

    @Schema(description = "Anti-affinity enforcement strategies")
    public enum AntiAffinityStrategy {
        @Schema(description = "No anti-affinity rules")
        NONE,

        @Schema(description = "Best effort to spread nodes, but allow same host if needed")
        SOFT,

        @Schema(description = "Strictly enforce anti-affinity, fail if cannot spread")
        HARD,

        @Schema(description = "Spread across failure domains (racks/zones)")
        ZONE_AWARE
    }
}
