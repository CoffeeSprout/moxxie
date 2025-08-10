package com.coffeesprout.service;

import com.coffeesprout.api.dto.TaskListResponse;
import com.coffeesprout.api.dto.TaskLogResponse;
import com.coffeesprout.api.dto.TaskStatusDetailResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.TaskListData;
import com.coffeesprout.client.TaskStatusData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class TaskService {
    
    private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    NodeService nodeService;
    
    @Inject
    TicketManager ticketManager;
    
    /**
     * Get detailed status of a specific task
     */
    public TaskStatusDetailResponse getTaskStatus(String upid, @AuthTicket String ticket) {
        LOG.debug("Getting status for task: {}", upid);
        
        try {
            // Parse UPID to extract node
            UPIDInfo upidInfo = parseUPID(upid);
            if (upidInfo == null) {
                throw new IllegalArgumentException("Invalid UPID format: " + upid);
            }
            
            // Don't encode here - JAX-RS will handle path parameter encoding
            
            // Get task status from Proxmox
            JsonNode response = proxmoxClient.getTaskStatus(upidInfo.node, upid, ticket);
            
            if (response == null || !response.has("data")) {
                throw new RuntimeException("No task data returned from Proxmox for UPID: " + upid);
            }
            
            JsonNode data = response.get("data");
            
            // Extract fields
            String status = data.has("status") ? data.get("status").asText() : "unknown";
            String exitstatus = data.has("exitstatus") ? data.get("exitstatus").asText() : null;
            
            // Determine if finished based on status
            Boolean finished = false;
            if (data.has("finished")) {
                finished = data.get("finished").asBoolean();
            } else if ("stopped".equals(status)) {
                // Task is finished if status is "stopped"
                finished = true;
            }
            
            Long starttime = data.has("starttime") ? data.get("starttime").asLong() : upidInfo.starttime;
            Long endtime = data.has("endtime") ? data.get("endtime").asLong() : null;
            
            // Calculate progress (0-1 range)
            Double progress = null;
            if (data.has("status") && !finished) {
                // Try to parse progress from status string
                String statusStr = data.get("status").asText();
                progress = parseProgress(statusStr);
            }
            
            return TaskStatusDetailResponse.create(
                upid,
                upidInfo.node,
                upidInfo.type,
                status,
                exitstatus,
                finished,
                progress,
                starttime,
                endtime
            );
            
        } catch (Exception e) {
            LOG.error("Failed to get task status for {}: {}", upid, e.getMessage());
            throw new RuntimeException("Failed to get task status for UPID " + upid + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Get task execution logs
     */
    public TaskLogResponse getTaskLog(String upid, Integer start, Integer limit, @AuthTicket String ticket) {
        LOG.debug("Getting logs for task: {} (start: {}, limit: {})", upid, start, limit);
        
        try {
            // Parse UPID to extract node
            UPIDInfo upidInfo = parseUPID(upid);
            if (upidInfo == null) {
                throw new IllegalArgumentException("Invalid UPID format: " + upid);
            }
            
            // Don't encode here - JAX-RS will handle path parameter encoding
            
            // Default pagination
            if (start == null) start = 0;
            if (limit == null) limit = 50;
            
            // Get task log from Proxmox
            JsonNode response = proxmoxClient.getTaskLog(upidInfo.node, upid, start, limit, ticket);
            
            LOG.debug("Retrieved log response for task {}: {}", upid, response);
            
            if (response == null || !response.has("data")) {
                return TaskLogResponse.create(upid, 0, start, Collections.emptyList(), true);
            }
            
            JsonNode data = response.get("data");
            
            // Convert log lines - Proxmox returns an array of objects with 'n' (line number) and 't' (text)
            List<String> logLines = new ArrayList<>();
            Integer totalLines = null;
            
            if (data.isArray()) {
                for (JsonNode line : data) {
                    if (line.has("t")) {
                        logLines.add(line.get("t").asText());
                    }
                    if (line.has("n")) {
                        totalLines = line.get("n").asInt();
                    }
                }
            }
            
            // Check if there are more logs
            boolean complete = totalLines == null || totalLines <= (start + logLines.size());
            
            return TaskLogResponse.create(
                upid,
                totalLines,
                start,
                logLines,
                complete
            );
            
        } catch (Exception e) {
            LOG.error("Failed to get task log for {}: {}", upid, e.getMessage());
            throw new RuntimeException("Failed to get task log for UPID " + upid + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * List tasks across all nodes or filter by criteria
     */
    public TaskListResponse listTasks(String node, String typeFilter, String statusFilter, 
                                     String userFilter, Integer vmid, Integer start, 
                                     Integer limit, String ticket) {
        LOG.debug("Listing tasks with filters - node: {}, type: {}, status: {}", 
                 node, typeFilter, statusFilter);
        
        try {
            List<TaskListResponse.TaskSummary> allTasks = new ArrayList<>();
            
            // Determine which nodes to query
            List<String> nodesToQuery = new ArrayList<>();
            if (node != null && !node.isEmpty()) {
                nodesToQuery.add(node);
            } else {
                // Query all nodes
                var nodes = nodeService.listNodes(ticket);
                nodesToQuery = nodes.stream()
                    .map(n -> n.getName())
                    .collect(Collectors.toList());
            }
            
            // Query each node
            for (String queryNode : nodesToQuery) {
                try {
                    TaskListData nodeTaskData = proxmoxClient.getNodeTasks(
                        queryNode, 
                        null,  // Let Proxmox handle pagination at node level
                        500,   // Get more tasks per node
                        statusFilter,
                        typeFilter,
                        userFilter,
                        vmid,
                        ticket
                    );
                    
                    if (nodeTaskData != null && nodeTaskData.getData() != null) {
                        for (TaskStatusData task : nodeTaskData.getData()) {
                            allTasks.add(TaskListResponse.TaskSummary.fromTask(
                                task.getUpid(),
                                task.getNode(),
                                task.getType(),
                                task.getStatus(),
                                task.getUser(),
                                task.getStarttime(),
                                task.getEndtime(),
                                task.getExitstatus(),
                                task.getFinished()
                            ));
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to get tasks from node {}: {}", queryNode, e.getMessage());
                }
            }
            
            // Sort by start time (newest first)
            allTasks.sort((a, b) -> {
                if (b.starttime() == null) return -1;
                if (a.starttime() == null) return 1;
                return b.starttime().compareTo(a.starttime());
            });
            
            // Apply pagination
            int totalTasks = allTasks.size();
            if (start == null) start = 0;
            if (limit == null) limit = 50;
            
            int endIndex = Math.min(start + limit, totalTasks);
            List<TaskListResponse.TaskSummary> pagedTasks = allTasks.subList(start, endIndex);
            
            return new TaskListResponse(pagedTasks, totalTasks, pagedTasks.size());
            
        } catch (Exception e) {
            LOG.error("Failed to list tasks: {}", e.getMessage());
            throw new RuntimeException("Failed to list tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Wait for a task to complete
     * @return true if task completed successfully, false otherwise
     */
    public boolean waitForTask(String node, String upid, int timeoutSeconds, @AuthTicket String ticket) {
        LOG.info("Waiting for task {} on node {} with timeout {}s", upid, node, timeoutSeconds);
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                TaskStatusDetailResponse status = getTaskStatus(upid, ticket);
                
                if (status.finished()) {
                    boolean success = "OK".equals(status.exitstatus());
                    LOG.info("Task {} finished with status: {}", upid, status.exitstatus());
                    return success;
                }
                
                // Wait 2 seconds before checking again
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Interrupted while waiting for task", e);
                return false;
            } catch (Exception e) {
                LOG.error("Error checking task status", e);
                // Continue waiting - task might still be running
            }
        }
        
        LOG.warn("Task {} timed out after {} seconds", upid, timeoutSeconds);
        return false;
    }
    
    /**
     * Stop a running task
     */
    public void stopTask(String upid, @AuthTicket String ticket) {
        LOG.info("Stopping task: {}", upid);
        
        try {
            // Parse UPID to extract node
            UPIDInfo upidInfo = parseUPID(upid);
            if (upidInfo == null) {
                throw new IllegalArgumentException("Invalid UPID format: " + upid);
            }
            
            // Don't encode here - JAX-RS will handle path parameter encoding
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Stop the task
            JsonNode response = proxmoxClient.stopTask(upidInfo.node, upid, ticket, csrfToken);
            
            if (response != null && response.has("data")) {
                LOG.info("Task stop initiated: {}", response.get("data").asText());
            }
            
        } catch (Exception e) {
            LOG.error("Failed to stop task {}: {}", upid, e.getMessage());
            throw new RuntimeException("Failed to stop task " + upid + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse UPID to extract components
     * Format: UPID:node:pid:pstart:starttime:type:id:user:
     */
    private UPIDInfo parseUPID(String upid) {
        if (upid == null || !upid.startsWith("UPID:")) {
            LOG.debug("Invalid UPID format - doesn't start with UPID: {}", upid);
            return null;
        }
        
        // Split with -1 to include trailing empty strings
        String[] parts = upid.split(":", -1);
        if (parts.length < 8) {
            LOG.debug("Invalid UPID format - not enough parts: {} (found {})", upid, parts.length);
            return null;
        }
        
        try {
            // Parse hex values
            long pstart = Long.parseLong(parts[3], 16); // pstart is hex
            long starttime = Long.parseLong(parts[4], 16); // starttime is hex
            
            return new UPIDInfo(
                parts[1], // node
                parts[2], // pid
                pstart,
                starttime,
                parts[5], // type
                parts[6], // id
                parts.length > 7 && !parts[7].isEmpty() ? parts[7] : null // user
            );
        } catch (NumberFormatException e) {
            LOG.error("Failed to parse UPID numbers: {} - Error: {}", upid, e.getMessage());
            return null;
        }
    }
    
    /**
     * Try to parse progress from status string
     */
    private Double parseProgress(String status) {
        if (status == null) return null;
        
        // Look for percentage pattern (e.g., "50%", "downloading: 50%")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)%");
        java.util.regex.Matcher matcher = pattern.matcher(status);
        
        if (matcher.find()) {
            try {
                double percent = Double.parseDouble(matcher.group(1));
                return percent / 100.0; // Convert to 0-1 range
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return null;
    }
    
    /**
     * Helper class to hold parsed UPID information
     */
    private static class UPIDInfo {
        final String node;
        final String pid;
        final long pstart;
        final long starttime;
        final String type;
        final String id;
        final String user;
        
        UPIDInfo(String node, String pid, long pstart, long starttime, 
                 String type, String id, String user) {
            this.node = node;
            this.pid = pid;
            this.pstart = pstart;
            this.starttime = starttime;
            this.type = type;
            this.id = id;
            this.user = user;
        }
    }
}