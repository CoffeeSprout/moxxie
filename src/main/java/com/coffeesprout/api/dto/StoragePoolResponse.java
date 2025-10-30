package com.coffeesprout.api.dto;

import java.util.List;

/**
 * Response DTO for storage pool information
 * This is a simple record for the existing storage API
 */
public record StoragePoolResponse(
    String storage,
    String type,
    boolean active,
    boolean shared,
    List<String> contentTypes,
    List<String> nodes,
    long totalCapacity,
    long totalUsed,
    long totalAvailable
) {
    public static StoragePoolResponse create(String storage, String type, boolean active,
                                           boolean shared, List<String> contentTypes,
                                           List<String> nodes, long totalCapacity,
                                           long totalUsed, long totalAvailable) {
        return new StoragePoolResponse(storage, type, active, shared, contentTypes,
                                     nodes, totalCapacity, totalUsed, totalAvailable);
    }

    // Utility method for formatting bytes
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
