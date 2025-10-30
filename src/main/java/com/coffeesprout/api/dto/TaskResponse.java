package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Response for asynchronous operations that return a task ID")
public record TaskResponse(
    @Schema(description = "Proxmox Unique Process ID (UPID) for tracking the task",
            required = true,
            example = "UPID:hv1:00001234:5F3E6789:12345678:qmcreate:123:root@pam:")
    String taskId,

    @Schema(description = "Human-readable message about the operation",
            example = "Snapshot creation started")
    String message
) {
    // Convenience constructor for just taskId
    public TaskResponse(String taskId) {
        this(taskId, "Operation started");
    }
}
