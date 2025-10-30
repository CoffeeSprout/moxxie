package com.coffeesprout.service;

import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.service.AuthTicket;
import com.coffeesprout.service.AutoAuthenticate;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.service.TicketManager;
import com.coffeesprout.util.TagUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;

@ApplicationScoped
@AutoAuthenticate
public class TagService {
    
    private static final Logger LOG = LoggerFactory.getLogger(TagService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    VMService vmService;
    
    @Inject
    VMLocatorService vmLocatorService;
    
    public Set<String> getVMTags(int vmId, @AuthTicket String ticket) {
        try {
            Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
            if (nodeOpt.isEmpty()) {
                LOG.error("Could not find node for VM {}", vmId);
                throw ProxmoxException.notFound("VM", String.valueOf(vmId),
                    "VM may not exist or is not accessible");
            }
            String node = nodeOpt.get();

            // Get VM config which includes tags
            var config = proxmoxClient.getVMConfig(
                node,
                vmId,
                ticket,
                ticketManager.getCsrfToken()
            );

            String tagsString = config.path("data").path("tags").asText("");
            return TagUtils.parseVMTags(tagsString);
        } catch (ProxmoxException e) {
            // Re-throw ProxmoxException as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Error getting tags for VM {}", vmId, e);
            throw ProxmoxException.vmOperationFailed("get tags", vmId, e.getMessage());
        }
    }
    
    public void addTag(int vmId, String tag, @AuthTicket String ticket) {
        try {
            Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
            if (nodeOpt.isEmpty()) {
                LOG.error("Could not find node for VM {}", vmId);
                throw ProxmoxException.notFound("VM", String.valueOf(vmId),
                    "VM may not exist or is not accessible");
            }
            String node = nodeOpt.get();

            Set<String> currentTags = getVMTags(vmId, ticket);
            currentTags.add(tag);

            String tagsString = TagUtils.tagsToString(currentTags);

            // URL-encode the tags value to handle special characters
            String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);

            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticket,
                ticketManager.getCsrfToken(),
                "tags=" + encodedTags
            );

            LOG.info("Added tag '{}' to VM {}", tag, vmId);
        } catch (ProxmoxException e) {
            // Re-throw ProxmoxException as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Error adding tag '{}' to VM {}", tag, vmId, e);
            throw ProxmoxException.vmOperationFailed("add tag", vmId, e.getMessage());
        }
    }
    
    public void removeTag(int vmId, String tag, @AuthTicket String ticket) {
        try {
            Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
            if (nodeOpt.isEmpty()) {
                LOG.error("Could not find node for VM {}", vmId);
                throw ProxmoxException.notFound("VM", String.valueOf(vmId),
                    "VM may not exist or is not accessible");
            }
            String node = nodeOpt.get();

            Set<String> currentTags = getVMTags(vmId, ticket);
            currentTags.remove(tag);

            String tagsString = TagUtils.tagsToString(currentTags);

            // URL-encode the tags value to handle special characters
            String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);

            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticket,
                ticketManager.getCsrfToken(),
                "tags=" + encodedTags
            );

            LOG.info("Removed tag '{}' from VM {}", tag, vmId);
        } catch (ProxmoxException e) {
            // Re-throw ProxmoxException as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Error removing tag '{}' from VM {}", tag, vmId, e);
            throw ProxmoxException.vmOperationFailed("remove tag", vmId, e.getMessage());
        }
    }
    
    /**
     * Update VM tags by replacing all existing tags
     */
    public void updateVMTags(int vmId, List<String> newTags, @AuthTicket String ticket) {
        try {
            Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
            if (nodeOpt.isEmpty()) {
                LOG.warn("Could not find node for VM {}", vmId);
                return;
            }
            String node = nodeOpt.get();
            
            String tagsString = newTags != null ? String.join(",", newTags) : "";
            
            // URL-encode the tags value to handle special characters
            String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
            
            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticket,
                ticketManager.getCsrfToken(),
                "tags=" + encodedTags
            );
            
            LOG.info("Updated tags for VM {} to: {}", vmId, tagsString);
        } catch (ProxmoxException e) {
            // Re-throw ProxmoxException as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Error updating tags for VM {}", vmId, e);
            throw ProxmoxException.vmOperationFailed("update tags", vmId, e.getMessage());
        }
    }
    
    /**
     * Get all unique tags in use across all VMs
     */
    public Set<String> getAllUniqueTags(@AuthTicket String ticket) {
        Set<String> allTags = new HashSet<>();
        try {
            var resources = proxmoxClient.getClusterResources(
                ticket,
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
            return allTags;
        } catch (Exception e) {
            LOG.error("Error getting all unique tags", e);
            throw ProxmoxException.internalError("get all unique tags", e);
        }
    }
    
    /**
     * Get VMs by specific tag
     */
    public List<Integer> getVMsByTag(String tag, @AuthTicket String ticket) {
        List<Integer> vmIds = new ArrayList<>();
        try {
            var resources = proxmoxClient.getClusterResources(
                ticket,
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
    public Map<Integer, String> bulkAddTags(List<Integer> vmIds, Set<String> tagsToAdd, @AuthTicket String ticket) {
        Map<Integer, String> results = new HashMap<>();
        
        for (Integer vmId : vmIds) {
            try {
                Set<String> currentTags = getVMTags(vmId, ticket);
                currentTags.addAll(tagsToAdd);
                
                Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
                if (nodeOpt.isEmpty()) {
                    results.put(vmId, "error: VM not found");
                    continue;
                }
                String node = nodeOpt.get();
                
                String tagsString = TagUtils.tagsToString(currentTags);
                
                // URL-encode the tags value to handle special characters
                String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
                
                proxmoxClient.updateVMConfig(
                    node,
                    vmId,
                    ticket,
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
    public Map<Integer, String> bulkRemoveTags(List<Integer> vmIds, Set<String> tagsToRemove, @AuthTicket String ticket) {
        Map<Integer, String> results = new HashMap<>();
        
        for (Integer vmId : vmIds) {
            try {
                Set<String> currentTags = getVMTags(vmId, ticket);
                currentTags.removeAll(tagsToRemove);
                
                Optional<String> nodeOpt = vmLocatorService.findNodeForVM(vmId, ticket);
                if (nodeOpt.isEmpty()) {
                    results.put(vmId, "error: VM not found");
                    continue;
                }
                String node = nodeOpt.get();
                
                String tagsString = TagUtils.tagsToString(currentTags);
                
                // URL-encode the tags value to handle special characters
                String encodedTags = java.net.URLEncoder.encode(tagsString, java.nio.charset.StandardCharsets.UTF_8);
                
                proxmoxClient.updateVMConfig(
                    node,
                    vmId,
                    ticket,
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
    public List<Integer> findVMsByNamePattern(String pattern, @AuthTicket String ticket) {
        List<Integer> vmIds = new ArrayList<>();
        Pattern namePattern = Pattern.compile(pattern.replace("*", ".*"));
        
        try {
            var resources = proxmoxClient.getClusterResources(
                ticket,
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
    
}