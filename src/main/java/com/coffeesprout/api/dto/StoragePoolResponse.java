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
}
