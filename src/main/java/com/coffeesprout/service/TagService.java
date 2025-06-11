package com.coffeesprout.service;

import com.coffeesprout.client.ProxmoxClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import java.util.HashSet;
import java.util.Set;

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
            return parseTags(tagsString);
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
            
            String tagsString = String.join(",", currentTags);
            
            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "tags=" + tagsString
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
            
            String tagsString = String.join(",", currentTags);
            
            // Update VM config with new tags
            proxmoxClient.updateVMConfig(
                node,
                vmId,
                ticketManager.getTicket(),
                ticketManager.getCsrfToken(),
                "tags=" + tagsString
            );
            
            LOG.info("Removed tag '" + tag + "' from VM " + vmId);
        } catch (Exception e) {
            LOG.error("Error removing tag from VM " + vmId, e);
        }
    }
    
    private Set<String> parseTags(String tagsString) {
        Set<String> tags = new HashSet<>();
        if (tagsString != null && !tagsString.trim().isEmpty()) {
            String[] tagArray = tagsString.split(",");
            for (String tag : tagArray) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        return tags;
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