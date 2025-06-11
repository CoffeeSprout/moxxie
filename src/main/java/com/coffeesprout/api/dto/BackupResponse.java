package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "VM backup information")
public record BackupResponse(
    @Schema(description = "Volume identifier", required = true, 
            example = "local:backup/vzdump-qemu-100-2024_01_15-10_30_00.vma.zst")
    String volid,
    
    @Schema(description = "Backup filename", required = true,
            example = "vzdump-qemu-100-2024_01_15-10_30_00.vma.zst")
    String filename,
    
    @Schema(description = "Backup size in bytes", required = true, example = "5368709120")
    Long size,
    
    @Schema(description = "Creation timestamp", required = true)
    Instant createdAt,
    
    @Schema(description = "Backup notes/description", example = "Pre-upgrade backup")
    String notes,
    
    @Schema(description = "Protection status", required = true)
    @JsonProperty("protected")
    Boolean isProtected,
    
    @Schema(description = "VM ID", required = true, example = "100")
    Integer vmId,
    
    @Schema(description = "Node where backup was created", required = true, example = "pve1")
    String node,
    
    @Schema(description = "Compression type used", example = "zstd")
    String compression,
    
    @Schema(description = "Encryption status", required = true)
    Boolean encrypted,
    
    @Schema(description = "Verification state", enumeration = {"ok", "failed", "none"})
    String verifyState,
    
    @Schema(description = "Storage location", required = true, example = "local")
    String storage,
    
    @Schema(description = "Human-readable size", example = "5.0 GB")
    String sizeHuman
) {
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1024) return String.format("%.1f GB", gb);
        double tb = gb / 1024.0;
        return String.format("%.1f TB", tb);
    }
}