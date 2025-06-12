package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record TaskStatusDetailResponse(
    @JsonProperty("upid")
    String upid,
    
    @JsonProperty("node")
    String node,
    
    @JsonProperty("pid")
    Integer pid,
    
    @JsonProperty("pstart")
    Long pstart,
    
    @JsonProperty("starttime")
    Long starttime,
    
    @JsonProperty("type")
    String type,
    
    @JsonProperty("id")
    String id,
    
    @JsonProperty("user")
    String user,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("exitstatus")
    String exitstatus,
    
    @JsonProperty("finished")
    Boolean finished,
    
    @JsonProperty("progress")
    Double progress,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("started_at")
    Instant startedAt,
    
    @JsonProperty("finished_at")
    Instant finishedAt,
    
    @JsonProperty("duration_seconds")
    Long durationSeconds
) {
    public static TaskStatusDetailResponse create(String upid, String node, String type, String status,
                                                   String exitstatus, Boolean finished, Double progress,
                                                   Long starttime, Long endtime) {
        if (upid == null) {
            throw new IllegalArgumentException("UPID cannot be null");
        }
        
        Instant startedAt = starttime != null ? Instant.ofEpochSecond(starttime) : null;
        Instant finishedAt = endtime != null ? Instant.ofEpochSecond(endtime) : null;
        Long duration = null;
        
        if (starttime != null) {
            if (endtime != null) {
                duration = endtime - starttime;
            } else {
                duration = Instant.now().getEpochSecond() - starttime;
            }
        }
        
        // Parse UPID components
        String[] parts = upid != null ? upid.split(":") : new String[0];
        Integer pid = null;
        Long pstart = null;
        
        // PID and pstart are hex values
        try {
            if (parts.length > 2 && !parts[2].isEmpty()) {
                pid = Integer.parseInt(parts[2], 16);
            }
            if (parts.length > 3 && !parts[3].isEmpty()) {
                pstart = Long.parseLong(parts[3], 16);
            }
        } catch (NumberFormatException e) {
            // Ignore parse errors
        }
        
        String taskId = (parts.length > 6 && !parts[6].isEmpty()) ? parts[6] : null;
        String user = (parts.length > 7 && !parts[7].isEmpty()) ? parts[7] : null;
        
        // Generate human-readable description
        String description = generateDescription(type, status, exitstatus, finished);
        
        return new TaskStatusDetailResponse(
            upid, node, pid, pstart, starttime, type, taskId, user,
            status, exitstatus, finished, progress, description,
            startedAt, finishedAt, duration
        );
    }
    
    private static String generateDescription(String type, String status, String exitstatus, Boolean finished) {
        StringBuilder desc = new StringBuilder();
        
        // Task type description
        switch (type) {
            case "qmcreate":
                desc.append("Creating VM");
                break;
            case "qmstart":
                desc.append("Starting VM");
                break;
            case "qmstop":
                desc.append("Stopping VM");
                break;
            case "qmshutdown":
                desc.append("Shutting down VM");
                break;
            case "qmsuspend":
                desc.append("Suspending VM");
                break;
            case "qmresume":
                desc.append("Resuming VM");
                break;
            case "qmreboot":
                desc.append("Rebooting VM");
                break;
            case "qmdestroy":
                desc.append("Destroying VM");
                break;
            case "qmsnapshot":
                desc.append("Creating snapshot");
                break;
            case "qmdelsnapshot":
                desc.append("Deleting snapshot");
                break;
            case "qmrollback":
                desc.append("Rolling back to snapshot");
                break;
            case "vzdump":
                desc.append("Creating backup");
                break;
            case "qmrestore":
                desc.append("Restoring from backup");
                break;
            case "imgcopy":
                desc.append("Copying disk image");
                break;
            case "download":
                desc.append("Downloading file");
                break;
            case "upload":
                desc.append("Uploading file");
                break;
            default:
                desc.append("Task ").append(type);
        }
        
        // Status
        if (Boolean.TRUE.equals(finished)) {
            desc.append(" - ");
            if ("OK".equals(exitstatus)) {
                desc.append("Completed successfully");
            } else if (exitstatus != null) {
                desc.append("Failed: ").append(exitstatus);
            } else {
                desc.append("Completed");
            }
        } else {
            desc.append(" - ").append(status != null ? status : "Running");
        }
        
        return desc.toString();
    }
}