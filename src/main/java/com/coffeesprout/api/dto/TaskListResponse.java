package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;

@RegisterForReflection
public record TaskListResponse(
    @JsonProperty("tasks")
    List<TaskSummary> tasks,
    
    @JsonProperty("total")
    Integer total,
    
    @JsonProperty("filtered")
    Integer filtered
) {
    @RegisterForReflection
    public record TaskSummary(
        @JsonProperty("upid")
        String upid,
        
        @JsonProperty("node")
        String node,
        
        @JsonProperty("type")
        String type,
        
        @JsonProperty("status")
        String status,
        
        @JsonProperty("user")
        String user,
        
        @JsonProperty("starttime")
        Instant starttime,
        
        @JsonProperty("endtime")
        Instant endtime,
        
        @JsonProperty("description")
        String description,
        
        @JsonProperty("exitstatus")
        String exitstatus,
        
        @JsonProperty("finished")
        Boolean finished,
        
        @JsonProperty("duration_seconds")
        Long durationSeconds
    ) {
        public static TaskSummary fromTask(String upid, String node, String type, String status,
                                          String user, Long starttime, Long endtime, 
                                          String exitstatus, Integer finished) {
            // Parse timestamp
            Instant start = starttime != null ? Instant.ofEpochSecond(starttime) : null;
            Instant end = endtime != null ? Instant.ofEpochSecond(endtime) : null;
            
            // Calculate duration
            Long duration = null;
            if (starttime != null && endtime != null) {
                duration = endtime - starttime;
            }
            
            // Determine if task is finished
            boolean isFinished = false;
            if (finished != null && finished == 1) {
                isFinished = true;
            } else if ("stopped".equals(status) || "OK".equals(status) || "job errors".equals(status)) {
                // Task is finished if status indicates completion
                isFinished = true;
            }
            
            // Generate description
            String desc = generateTaskDescription(type, status, exitstatus, isFinished);
            
            return new TaskSummary(upid, node, type, status, user, start, end, 
                                  desc, exitstatus, isFinished, duration);
        }
        
        private static String generateTaskDescription(String type, String status, 
                                                     String exitstatus, boolean finished) {
            String taskDesc = switch (type) {
                case "qmcreate" -> "VM creation";
                case "qmstart" -> "VM start";
                case "qmstop" -> "VM stop";
                case "qmshutdown" -> "VM shutdown";
                case "qmdestroy" -> "VM deletion";
                case "qmsnapshot" -> "Snapshot creation";
                case "qmdelsnapshot" -> "Snapshot deletion";
                case "qmrollback" -> "Snapshot rollback";
                case "vzdump" -> "Backup";
                case "qmrestore" -> "VM restore";
                case "imgcopy" -> "Disk copy";
                case "download" -> "Download";
                case "upload" -> "Upload";
                default -> type;
            };
            
            if (finished) {
                if ("OK".equals(exitstatus)) {
                    return taskDesc + " completed";
                } else if (exitstatus != null && !exitstatus.isEmpty()) {
                    return taskDesc + " failed: " + exitstatus;
                } else if ("OK".equals(status)) {
                    return taskDesc + " completed";
                } else if ("job errors".equals(status)) {
                    return taskDesc + " completed with errors";
                } else {
                    return taskDesc + " completed";
                }
            } else {
                return taskDesc + " in progress";
            }
        }
    }
}