package com.coffeesprout.service;

import com.coffeesprout.client.*;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.util.TagUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class VMService {

    private static final Logger log = LoggerFactory.getLogger(VMService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    TagService tagService;
    
    @Inject
    ObjectMapper objectMapper;

    @SafeMode(value = false)  // Read operation
    public List<VMResponse> listVMs(@AuthTicket String ticket) {
        return listVMsWithFilters(null, null, null, null, ticket);
    }
    
    @SafeMode(value = false)  // Read operation
    public List<VMResponse> listVMsWithFilters(List<String> tags, String client, String node, String status, @AuthTicket String ticket) {
        try {
            // Get all VMs from cluster
            JsonNode resources = proxmoxClient.getClusterResources(ticket, ticketManager.getCsrfToken(), "vm");
            List<VMResponse> vms = new ArrayList<>();
            
            if (resources != null && resources.has("data")) {
                JsonNode dataArray = resources.get("data");
                
                for (JsonNode resource : dataArray) {
                    // Skip if not a VM
                    String type = resource.path("type").asText("");
                    if (!"qemu".equals(type)) {
                        continue;
                    }
                    
                    // Parse VM data
                    Map<String, Object> vmData = objectMapper.convertValue(resource, Map.class);
                    
                    // Parse tags
                    String tagsString = resource.path("tags").asText("");
                    Set<String> vmTags = TagUtils.parseVMTags(tagsString);
                    List<String> vmTagsList = new ArrayList<>(vmTags);
                    
                    // Debug logging
                    if (!tagsString.isEmpty() && log.isDebugEnabled()) {
                        log.debug("VM {} has tags string '{}' parsed to: {}", 
                            resource.path("vmid").asInt(), tagsString, vmTags);
                    }
                    
                    // Apply filters
                    
                    // Tag filter - VM must have ALL specified tags
                    if (tags != null && !tags.isEmpty()) {
                        boolean hasAllTags = tags.stream().allMatch(vmTags::contains);
                        if (!hasAllTags) {
                            continue;
                        }
                    }
                    
                    // Client filter - convenience filter for client:<name> tag
                    if (client != null && !client.isEmpty()) {
                        String clientTag = TagUtils.client(client);
                        if (!vmTags.contains(clientTag)) {
                            continue;
                        }
                    }
                    
                    // Node filter
                    if (node != null && !node.isEmpty()) {
                        String vmNode = resource.path("node").asText("");
                        if (!node.equals(vmNode)) {
                            continue;
                        }
                    }
                    
                    // Status filter
                    if (status != null && !status.isEmpty()) {
                        String vmStatus = resource.path("status").asText("");
                        if (!status.equals(vmStatus)) {
                            continue;
                        }
                    }
                    
                    // Get pool information (if available in the response)
                    String pool = resource.path("pool").asText(null);
                    
                    // Create enhanced VM response
                    VMResponse vmResponse = new VMResponse(
                        resource.path("vmid").asInt(),
                        resource.path("name").asText(""),
                        resource.path("node").asText(""),
                        resource.path("status").asText(""),
                        resource.path("cpus").asInt(0),
                        resource.path("maxmem").asLong(0),
                        resource.path("maxdisk").asLong(0),
                        resource.path("uptime").asLong(0),
                        resource.path("type").asText(""),
                        vmTagsList,
                        pool
                    );
                    vms.add(vmResponse);
                }
            }
            
            return vms;
        } catch (Exception e) {
            log.error("Error listing VMs with filters", e);
            throw new RuntimeException("Failed to list VMs: " + e.getMessage(), e);
        }
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public CreateVMResponse createVM(String node, CreateVMRequest request, @AuthTicket String ticket) {
        // Create the VM
        CreateVMResponse response = proxmoxClient.createVM(node, ticket, ticketManager.getCsrfToken(), request);
        
        // Auto-tag the VM if it was created successfully
        if (response != null && request.getVmid() != 0 && request.getName() != null) {
            try {
                // Generate auto-tags based on VM name
                Set<String> autoTags = TagUtils.autoGenerateTags(request.getName());
                
                // Add any tags from the request
                if (request.getTags() != null && !request.getTags().isEmpty()) {
                    Set<String> requestTags = TagUtils.parseVMTags(request.getTags());
                    autoTags.addAll(requestTags);
                }
                
                // Apply tags to the VM
                if (!autoTags.isEmpty()) {
                    tagService.bulkAddTags(List.of(request.getVmid()), autoTags);
                    log.info("Auto-tagged VM {} with tags: {}", request.getVmid(), autoTags);
                }
            } catch (Exception e) {
                log.warn("Failed to auto-tag VM {}: {}", request.getVmid(), e.getMessage());
                // Don't fail the VM creation just because tagging failed
            }
        }
        
        return response;
    }

    public ImportImageResponse importImage(String node, String storage, ImportImageRequest request, @AuthTicket String ticket) {
        return proxmoxClient.importCloudImage(node, storage, ticket, request);
    }

    @SafeMode(value = false)  // Read operation
    public VMStatusResponse getVMStatus(String node, int vmid, @AuthTicket String ticket) {
        log.debug("Getting detailed status for VM {} on node {}", vmid, node);
        return proxmoxClient.getVMStatus(node, vmid, ticket);
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void startVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Starting VM {} on node {}", vmid, node);
        proxmoxClient.startVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void stopVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Stopping VM {} on node {}", vmid, node);
        proxmoxClient.stopVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }

    @SafeMode(operation = SafeMode.Operation.DELETE)
    public void deleteVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Deleting VM {} on node {}", vmid, node);
        proxmoxClient.deleteVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void rebootVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Rebooting VM {} on node {}", vmid, node);
        proxmoxClient.rebootVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void suspendVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Suspending VM {} on node {}", vmid, node);
        proxmoxClient.suspendVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void resumeVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Resuming VM {} on node {}", vmid, node);
        proxmoxClient.resumeVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void shutdownVM(String node, int vmid, @AuthTicket String ticket) {
        log.info("Shutting down VM {} on node {}", vmid, node);
        proxmoxClient.shutdownVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(value = false)  // Read operation
    public Map<String, Object> getVMConfig(String node, int vmId, @AuthTicket String ticket) {
        log.debug("Getting VM config for VM {} on node '{}'", vmId, node);
        VMConfigResponse response = proxmoxClient.getVMConfig(node, vmId, ticket);
        return response.getData() != null ? response.getData() : new HashMap<>();
    }
}