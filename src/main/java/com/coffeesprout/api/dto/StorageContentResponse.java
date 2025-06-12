package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response DTO for individual storage content items
 */
public record StorageContentResponse(
    String volid,
    String name,
    long size,
    String format,
    @JsonProperty("created_at")
    Instant createdAt,
    String notes,
    @JsonProperty("protected")
    boolean isProtected,
    @JsonProperty("content_type")
    String contentType,
    @JsonProperty("formatted_size")
    String formattedSize,
    Integer vmid,
    String verification
) {
    
    /**
     * Create a StorageContentResponse from raw data
     */
    public static StorageContentResponse create(String volid, String filename, long size,
                                               String format, long ctime, String notes,
                                               boolean isProtected, String contentType,
                                               Integer vmid, String verification) {
        return new StorageContentResponse(
            volid,
            filename,
            size,
            format,
            Instant.ofEpochSecond(ctime),
            notes,
            isProtected,
            contentType,
            StoragePoolResponse.formatBytes(size),
            vmid,
            verification
        );
    }
}