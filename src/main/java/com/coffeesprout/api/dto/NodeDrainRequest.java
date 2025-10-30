package com.coffeesprout.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request to drain a node by migrating all VMs off it.
 */
@RegisterForReflection
public record NodeDrainRequest(
    String drainMode,      // "soft" (maintenance/reboot) or "hard" (faulty machine)
    Boolean parallel,
    Integer maxConcurrent,
    Boolean allowOffline,
    String targetNode
) {
    /**
     * Soft drain - for routine maintenance/reboots.
     * Skips VMs with maint-ok tag (they can handle downtime).
     * Always-on VMs are live migrated.
     */
    public static NodeDrainRequest softDrain() {
        return new NodeDrainRequest(
            "soft",
            true,  // parallel migrations
            3,     // max 3 concurrent migrations
            false, // no offline migration for soft drain
            null   // auto-select target nodes
        );
    }

    /**
     * Hard drain - for clearing faulty machines.
     * Migrates ALL VMs including maint-ok.
     * Offline migration allowed for maint-ok VMs.
     */
    public static NodeDrainRequest hardDrain() {
        return new NodeDrainRequest(
            "hard",
            true,  // parallel migrations
            3,     // max 3 concurrent migrations
            true,  // allow offline migration
            null   // auto-select target nodes
        );
    }

    /**
     * Create default drain request (soft drain).
     */
    public static NodeDrainRequest withDefaults() {
        return softDrain();
    }

    /**
     * Get drain mode with default.
     */
    public String drainModeOrDefault() {
        return drainMode != null ? drainMode : "soft";
    }

    /**
     * Check if this is a soft drain (maintenance/reboot).
     */
    public boolean isSoftDrain() {
        return "soft".equals(drainModeOrDefault());
    }

    /**
     * Check if this is a hard drain (faulty machine).
     */
    public boolean isHardDrain() {
        return "hard".equals(drainModeOrDefault());
    }

    /**
     * Get parallel setting with default.
     */
    public boolean parallelOrDefault() {
        return parallel != null ? parallel : true;
    }

    /**
     * Get max concurrent with default.
     */
    public int maxConcurrentOrDefault() {
        return maxConcurrent != null ? maxConcurrent : 3;
    }

    /**
     * Get allow offline with default.
     */
    public boolean allowOfflineOrDefault() {
        return allowOffline != null ? allowOffline : false;
    }
}
