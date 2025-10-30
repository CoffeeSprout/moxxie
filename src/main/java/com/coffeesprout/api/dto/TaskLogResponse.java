package com.coffeesprout.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TaskLogResponse(
    @JsonProperty("upid")
    String upid,

    @JsonProperty("total_lines")
    Integer totalLines,

    @JsonProperty("start_line")
    Integer startLine,

    @JsonProperty("lines")
    List<TaskLogEntry> lines,

    @JsonProperty("complete")
    Boolean complete
) {
    @RegisterForReflection
    public record TaskLogEntry(
        @JsonProperty("line_number")
        Integer lineNumber,

        @JsonProperty("timestamp")
        String timestamp,

        @JsonProperty("message")
        String message
    ) {
        public static TaskLogEntry fromLogLine(Integer lineNumber, String logLine) {
            // Proxmox task logs often have format: "timestamp: message"
            int colonIndex = logLine.indexOf(": ");
            if (colonIndex > 0 && colonIndex < 30) { // Reasonable position for timestamp
                String timestamp = logLine.substring(0, colonIndex);
                String message = logLine.substring(colonIndex + 2);
                return new TaskLogEntry(lineNumber, timestamp, message);
            }
            return new TaskLogEntry(lineNumber, null, logLine);
        }
    }

    public static TaskLogResponse create(String upid, Integer total, Integer start,
                                         List<String> logLines, Boolean complete) {
        List<TaskLogEntry> entries = null;
        if (logLines != null) {
            entries = new java.util.ArrayList<>();
            for (int i = 0; i < logLines.size(); i++) {
                entries.add(TaskLogEntry.fromLogLine(start + i, logLines.get(i)));
            }
        }

        return new TaskLogResponse(upid, total, start, entries, complete);
    }
}
