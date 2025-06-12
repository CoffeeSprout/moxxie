package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for file upload operations
 */
@Schema(description = "Response from file upload operation")
public record UploadResponse(
    @Schema(description = "Task ID for tracking upload progress",
            example = "UPID:pve1:00001234:00112233:65A12345:upload:local:root@pam:")
    @JsonProperty("task_id")
    String taskId,
    
    @Schema(description = "Uploaded filename",
            example = "ubuntu-22.04.3-server.iso")
    String filename,
    
    @Schema(description = "Storage location",
            example = "local")
    String storage,
    
    @Schema(description = "Volume ID of uploaded file",
            example = "local:iso/ubuntu-22.04.3-server.iso")
    String volid,
    
    @Schema(description = "Human readable message",
            example = "Upload of ubuntu-22.04.3-server.iso to storage 'local' started")
    String message
) {
    
    /**
     * Create an UploadResponse for a successful upload initiation
     */
    public static UploadResponse create(String taskId, String filename, String storage) {
        String volid = String.format("%s:iso/%s", storage, filename);
        String message = String.format("Upload of %s to storage '%s' started", filename, storage);
        
        return new UploadResponse(taskId, filename, storage, volid, message);
    }
}