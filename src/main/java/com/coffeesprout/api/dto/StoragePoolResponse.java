package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for storage pool information with enhanced details
 */
public record StoragePoolResponse(
    String storage,
    String type,
    boolean enabled,
    boolean shared,
    List<String> content,
    List<String> nodes,
    long total,
    long used,
    long available,
    @JsonProperty("used_percentage")
    double usedPercentage,
    @JsonProperty("formatted_total")
    String formattedTotal,
    @JsonProperty("formatted_used") 
    String formattedUsed,
    @JsonProperty("formatted_available")
    String formattedAvailable
) {
    
    /**
     * Format bytes to human readable string
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        
        return String.format("%.1f %s", 
            bytes / Math.pow(1024, digitGroups), 
            units[digitGroups]);
    }
    
    /**
     * Create a StoragePoolResponse with formatted sizes
     */
    public static StoragePoolResponse create(String storage, String type, boolean enabled,
                                            boolean shared, List<String> content, 
                                            List<String> nodes, long total, long used,
                                            long available) {
        double usedPct = total > 0 ? (used * 100.0 / total) : 0;
        
        return new StoragePoolResponse(
            storage,
            type,
            enabled,
            shared,
            content,
            nodes,
            total,
            used,
            available,
            usedPct,
            formatBytes(total),
            formatBytes(used),
            formatBytes(available)
        );
    }
}