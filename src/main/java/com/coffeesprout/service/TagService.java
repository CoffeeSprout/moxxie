package com.coffeesprout.service;

import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.util.TagUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
public class TagService {
    
    private static final Logger LOG = Logger.getLogger(TagService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    public Set<String> getVMTags(int vmId) {
        try {
            String node = getNodeForVM(vmId);
            if (node == null) {
                LOG.warn("Could not find node for VM " + vmId);
                return new HashSet<>();
            }
            
            // Get VM config which includes tags
            var config = proxmoxClient.getVMConfig(
                node,
                vmId,
                ticketManager.getTicket(),
                ticketManager.getCsrfToken()
            );
            
            String tagsString = config.path("data").path("tags").asText("");
            return TagUtils.parseVMTags(tagsString);
        } catch (Exception e) {
            LOG.error("Error getting tags for VM " + vmId, e);
            return new HashSet<>();
        }
    }
    
    public void addTag(int vmId, String tag) {
        try {
            String node = getNodeForVM(vmId);
            if (node == null) {
                LOG.warn("Could not find node for VM " + vmId);
                return;
            }
            
            Set<String> currentTags = getVMTags(vmId);
            currentTags.add(tag);
            
            String tagsString = TagUtils.tagsToString(currentTags);
            
            // URL-encode the tags value to handle special characters
            String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
            
            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "tags=" + encodedTags
            );
            
            LOG.info("Added tag '" + tag + "' to VM " + vmId);
        } catch (Exception e) {
            LOG.error("Error adding tag to VM " + vmId, e);
        }
    }
    
    public void removeTag(int vmId, String tag) {
        try {
            String node = getNodeForVM(vmId);
            if (node == null) {
                LOG.warn("Could not find node for VM " + vmId);
                return;
            }
            
            Set<String> currentTags = getVMTags(vmId);
            currentTags.remove(tag);
            
            String tagsString = TagUtils.tagsToString(currentTags);
            
            // URL-encode the tags value to handle special characters
            String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
            
            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "tags=" + encodedTags
            );
            
            LOG.info("Removed tag '" + tag + "' from VM " + vmId);
        } catch (Exception e) {
            LOG.error("Error removing tag from VM " + vmId, e);
        }
    }
    
    /**
     * Get all unique tags in use across all VMs
     */
    public Set<String> getAllUniqueTags() {
        Set<String> allTags = new HashSet<>();
        try {
            var resources = proxmoxClient.getClusterResources(
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "vm"
            );
            
            if (resources != null && resources.has("data")) {
                var dataArray = resources.get("data");
                for (var resource : dataArray) {
                    if (resource.has("tags")) {
                        String tagsString = resource.get("tags").asText("");
                        allTags.addAll(TagUtils.parseVMTags(tagsString));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error getting all unique tags", e);
        }
        return allTags;
    }
    
    /**
     * Get VMs by specific tag
     */
    public List<Integer> getVMsByTag(String tag) {
        List<Integer> vmIds = new ArrayList<>();
        try {
            var resources = proxmoxClient.getClusterResources(
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "vm"
            );
            
            if (resources != null && resources.has("data")) {
                var dataArray = resources.get("data");
                for (var resource : dataArray) {
                    if (resource.has("tags") && resource.has("vmid")) {
                        String tagsString = resource.get("tags").asText("");
                        Set<String> tags = TagUtils.parseVMTags(tagsString);
                        if (tags.contains(tag)) {
                            vmIds.add(resource.get("vmid").asInt());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error getting VMs by tag: " + tag, e);
        }
        return vmIds;
    }
    
    /**
     * Bulk add tags to multiple VMs
     */
    public Map<Integer, String> bulkAddTags(List<Integer> vmIds, Set<String> tagsToAdd) {
        Map<Integer, String> results = new HashMap<>();
        
        for (Integer vmId : vmIds) {
            try {
                Set<String> currentTags = getVMTags(vmId);
                currentTags.addAll(tagsToAdd);
                
                String node = getNodeForVM(vmId);
                if (node == null) {
                    results.put(vmId, "error: VM not found");
                    continue;
                }
                
                String tagsString = TagUtils.tagsToString(currentTags);
                
                // URL-encode the tags value to handle special characters
                String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
                
                proxmoxClient.updateVMConfig(
                    node,
                    vmId,
                    ticketManager.getTicket(),
                    ticketManager.getCsrfToken(),
                    "tags=" + encodedTags
                );
                
                results.put(vmId, "success");
                LOG.info("Added tags to VM " + vmId + ": " + tagsToAdd);
            } catch (Exception e) {
                results.put(vmId, "error: " + e.getMessage());
                LOG.error("Error adding tags to VM " + vmId, e);
            }
        }
        
        return results;
    }
    
    /**
     * Bulk remove tags from multiple VMs
     */
    public Map<Integer, String> bulkRemoveTags(List<Integer> vmIds, Set<String> tagsToRemove) {
        Map<Integer, String> results = new HashMap<>();
        
        for (Integer vmId : vmIds) {
            try {
                Set<String> currentTags = getVMTags(vmId);
                currentTags.removeAll(tagsToRemove);
                
                String node = getNodeForVM(vmId);
                if (node == null) {
                    results.put(vmId, "error: VM not found");
                    continue;
                }
                
                String tagsString = TagUtils.tagsToString(currentTags);
                
                // URL-encode the tags value to handle special characters
                String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
                
                proxmoxClient.updateVMConfig(
                    node,
                    vmId,
                    ticketManager.getTicket(),
                    ticketManager.getCsrfToken(),
                    "tags=" + encodedTags
                );
                
                results.put(vmId, "success");
                LOG.info("Removed tags from VM " + vmId + ": " + tagsToRemove);
            } catch (Exception e) {
                results.put(vmId, "error: " + e.getMessage());
                LOG.error("Error removing tags from VM " + vmId, e);
            }
        }
        
        return results;
    }
    
    /**
     * Find VMs by name pattern
     */
    public List<Integer> findVMsByNamePattern(String pattern) {
        List<Integer> vmIds = new ArrayList<>();
        Pattern namePattern = Pattern.compile(pattern.replace("*", ".*"));
        
        try {
            var resources = proxmoxClient.getClusterResources(
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "vm"
            );
            
            if (resources != null && resources.has("data")) {
                var dataArray = resources.get("data");
                for (var resource : dataArray) {
                    if (resource.has("name") && resource.has("vmid")) {
                        String name = resource.get("name").asText("");
                        if (namePattern.matcher(name).matches()) {
                            vmIds.add(resource.get("vmid").asInt());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error finding VMs by name pattern: " + pattern, e);
        }
        
        return vmIds;
    }
    
    private String getNodeForVM(int vmId) {
        try {
            var resources = proxmoxClient.getClusterResources(
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "vm"
            );
            
            if (resources != null && resources.has("data")) {
                var dataArray = resources.get("data");
                for (var resource : dataArray) {
                    if (resource.has("vmid") && resource.get("vmid").asInt() == vmId) {
                        return resource.get("node").asText();
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error finding node for VM " + vmId, e);
        }
        return null;
    }
}