package com.coffeesprout.service;

import com.coffeesprout.client.*;
import com.coffeesprout.api.dto.CloudInitVMRequest;
import com.coffeesprout.api.dto.DiskConfig;
import com.coffeesprout.api.dto.FirmwareConfig;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
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
import java.nio.charset.StandardCharsets;

@ApplicationScoped
@AutoAuthenticate
public class VMService {

    private static final Logger LOG = LoggerFactory.getLogger(VMService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    TicketManager ticketManager;

    @Inject
    TagService tagService;

    @Inject
    MigrationService migrationService;

    @Inject
    VMIdService vmIdService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AnsibleCallbackService ansibleCallbackService;

    @SafeMode(false)  // Read operation
    public List<VMResponse> listVMs(@AuthTicket String ticket) {
        return listVMsWithFilters(null, null, null, null, ticket);
    }
    
    @SafeMode(false)  // Read operation
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
                    if (!tagsString.isEmpty() && LOG.isDebugEnabled()) {
                        LOG.debug("VM {} has tags string '{}' parsed to: {}", 
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
                        pool,
                        resource.path("template").asInt(0)
                    );
                    vms.add(vmResponse);
                }
            }
            
            return vms;
        } catch (Exception e) {
            LOG.error("Error listing VMs with filters", e);
            throw ProxmoxException.internalError("list VMs", e);
        }
    }

    /**
     * Find a VM by its ID across all nodes in the cluster.
     * This method eliminates the repeated pattern of listing all VMs and filtering by ID.
     * 
     * @param vmId The VM ID to search for
     * @param ticket Authentication ticket
     * @return VMResponse if found, null otherwise
     */
    public VMResponse findVmById(int vmId, @AuthTicket String ticket) {
        return listVMs(ticket).stream()
            .filter(vm -> vm.vmid() == vmId)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find a VM by its ID and throw exception if not found.
     * 
     * @param vmId The VM ID to search for
     * @param ticket Authentication ticket
     * @return VMResponse if found
     * @throws RuntimeException if VM not found
     */
    public VMResponse findVmByIdOrThrow(int vmId, @AuthTicket String ticket) {
        VMResponse vm = findVmById(vmId, ticket);
        if (vm == null) {
            throw ProxmoxException.notFound("VM", String.valueOf(vmId));
        }
        return vm;
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void resizeDisk(String node, int vmId, String disk, String size, @AuthTicket String ticket) {
        try {
            LOG.info("Resizing disk {} for VM {} to size {}", disk, vmId, size);
            
            // The disk parameter should be like "scsi0", size like "+20G" or "50G"
            String resizeParam = disk + "=" + size;
            
            // Use a generic update config method - we may need to add this to ProxmoxClient
            proxmoxClient.resizeDisk(node, vmId, disk, size, ticket, ticketManager.getCsrfToken());
            
            LOG.info("Successfully resized disk {} for VM {}", disk, vmId);
        } catch (Exception e) {
            LOG.error("Failed to resize disk for VM {}", vmId, e);
            throw ProxmoxException.vmOperationFailed("resize disk", vmId, e.getMessage());
        }
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public CreateVMResponse createVM(String node, CreateVMRequest request, @AuthTicket String ticket) {
        // Create the VM
        CreateVMResponse response = proxmoxClient.createVM(node, ticket, ticketManager.getCsrfToken(), request);
        
        // Set the VM ID and status in the response
        // Proxmox returns a task UPID in the data field, not the VM details
        if (response != null) {
            response.setVmid(request.getVmid());
            response.setStatus("created");
        }
        
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
                    tagService.bulkAddTags(List.of(request.getVmid()), autoTags, null);
                    LOG.info("Auto-tagged VM {} with tags: {}", request.getVmid(), autoTags);
                }
            } catch (Exception e) {
                LOG.warn("Failed to auto-tag VM {}: {}", request.getVmid(), e.getMessage());
                // Don't fail the VM creation just because tagging failed
            }
        }
        
        return response;
    }

    public ImportImageResponse importImage(String node, String storage, ImportImageRequest request, @AuthTicket String ticket) {
        return proxmoxClient.importCloudImage(node, storage, ticket, request);
    }

    @SafeMode(false)  // Read operation
    public VMStatusResponse getVMStatus(String node, int vmid, @AuthTicket String ticket) {
        LOG.debug("Getting detailed status for VM {} on node {}", vmid, node);
        return proxmoxClient.getVMStatus(node, vmid, ticket);
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void startVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Starting VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.startVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM start task initiated: {}", response.getData());
        }
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void stopVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Stopping VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.stopVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM stop task initiated: {}", response.getData());
        }
    }

    @SafeMode(operation = SafeMode.Operation.DELETE)
    public void deleteVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Deleting VM {} on node {}", vmid, node);
        proxmoxClient.deleteVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void rebootVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Rebooting VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.rebootVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM reboot task initiated: {}", response.getData());
        }
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void suspendVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Suspending VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.suspendVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM suspend task initiated: {}", response.getData());
        }
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void resumeVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Resuming VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.resumeVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM resume task initiated: {}", response.getData());
        }
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void shutdownVM(String node, int vmid, @AuthTicket String ticket) {
        LOG.info("Shutting down VM {} on node {}", vmid, node);
        TaskStatusResponse response = proxmoxClient.shutdownVM(node, vmid, ticket, ticketManager.getCsrfToken());
        if (response != null && response.getData() != null) {
            LOG.debug("VM shutdown task initiated: {}", response.getData());
        }
    }
    
    @SafeMode(false)  // Read operation
    public Map<String, Object> getVMConfig(String node, int vmId, @AuthTicket String ticket) {
        LOG.debug("Getting VM config for VM {} on node '{}'", vmId, node);
        VMConfigResponse response = proxmoxClient.getVMConfig(node, vmId, ticket);
        return response.getData() != null ? response.getData() : new HashMap<>();
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void updateVMConfig(String node, int vmId, CreateVMRequest config, @AuthTicket String ticket) {
        LOG.info("Updating VM {} configuration on node '{}'", vmId, node);
        
        // The Proxmox updateVMConfig method expects the config as form data
        // We need to use the generic update method with the CreateVMRequest
        String formData = buildFormData(config);
        
        proxmoxClient.updateVMConfig(node, vmId, ticket, ticketManager.getCsrfToken(), formData);
    }
    
    private String buildFormData(CreateVMRequest config) {
        StringBuilder formData = new StringBuilder();
        
        // Only include non-null fields
        if (config.getCiuser() != null) {
            appendFormParam(formData, "ciuser", config.getCiuser());
        }
        if (config.getCipassword() != null) {
            appendFormParam(formData, "cipassword", config.getCipassword());
        }
        if (config.getIpconfig0() != null) {
            appendFormParam(formData, "ipconfig0", config.getIpconfig0());
        }
        if (config.getNameserver() != null) {
            appendFormParam(formData, "nameserver", config.getNameserver());
        }
        if (config.getSearchdomain() != null) {
            appendFormParam(formData, "searchdomain", config.getSearchdomain());
        }
        if (config.getSshkeys() != null) {
            String sshKeys = config.getSshkeys().trim();
            
            // Log the exact SSH key we're about to encode
            LOG.info("Raw SSH keys before encoding: '{}'", sshKeys);
            LOG.info("SSH keys length: {}, ends with newline: {}", 
                    sshKeys.length(), sshKeys.endsWith("\n"));
            
            // Try the original approach with standard URL encoding
            appendFormParam(formData, "sshkeys", sshKeys);
            
            LOG.info("SSH keys added to form data");
        }
        if (config.getDescription() != null) {
            appendFormParam(formData, "description", config.getDescription());
        }
        if (config.getTags() != null) {
            appendFormParam(formData, "tags", config.getTags());
        }
        if (config.getBoot() != null) {
            appendFormParam(formData, "boot", config.getBoot());
        }
        
        return formData.toString();
    }
    
    private void appendFormParam(StringBuilder formData, String name, String value) {
        if (formData.length() > 0) {
            formData.append("&");
        }
        try {
            formData.append(name).append("=").append(java.net.URLEncoder.encode(value, "UTF-8"));
        } catch (Exception e) {
            throw ProxmoxException.internalError("encode form parameter: " + name, e);
        }
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void importDisk(String node, int vmId, String diskConfig, @AuthTicket String ticket) {
        LOG.info("Importing disk for VM {} on node '{}' with config: {}", vmId, node, diskConfig);
        
        // Use the updateDisk method from ProxmoxClient which is designed for disk import
        ConfigResponse response = proxmoxClient.updateDisk(node, vmId, diskConfig, ticket, ticketManager.getCsrfToken());

        if (response == null) {
            throw ProxmoxException.vmOperationFailed("import disk", vmId,
                "No response from Proxmox");
        }

        LOG.info("Disk import initiated successfully for VM {}", vmId);
    }
    
    /**
     * Set SSH keys on a VM using the proven double URL encoding method.
     * Proxmox API requires SSH keys to be double URL encoded.
     */
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void setSSHKeysDirect(String node, int vmId, String sshKeys, @AuthTicket String ticket) {
        LOG.info("Setting SSH keys on VM {} using double encoding", vmId);
        
        try {
            // First, normalize the SSH key
            String normalized = sshKeys.trim()
                .replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n");
            
            // Apply double URL encoding as required by Proxmox API
            // First encoding - replace + with %20 to match Python's quote behavior
            String encoded = java.net.URLEncoder.encode(normalized, "UTF-8")
                    .replaceAll("\\+", "%20");
            
            // Second encoding - also replace + with %20
            String doubleEncoded = java.net.URLEncoder.encode(encoded, "UTF-8")
                    .replaceAll("\\+", "%20");
            
            LOG.debug("SSH key double-encoded successfully");
            
            // Build the form data with the double-encoded value
            String formData = "sshkeys=" + doubleEncoded;
            
            // Send the request
            proxmoxClient.updateVMConfig(node, vmId, ticket, ticketManager.getCsrfToken(), formData);
            
            LOG.info("Successfully set SSH keys on VM {}", vmId);

        } catch (Exception e) {
            LOG.error("Failed to set SSH keys on VM {}", vmId, e);
            throw ProxmoxException.vmOperationFailed("set SSH keys", vmId, e.getMessage());
        }
    }
    
    /**
     * Clone a VM from a template or existing VM.
     * Creates a new VM based on an existing VM or template.
     */
    public TaskStatusResponse cloneVM(String node, int templateId, int newVmId, String name, 
                                    String description, boolean fullClone, String pool, 
                                    String snapname, String storage, String targetNode, 
                                    @AuthTicket String ticket) {
        try {
            LOG.info("Cloning VM {} to new VM {} on node {}", templateId, newVmId, targetNode != null ? targetNode : node);
            
            return proxmoxClient.cloneVM(
                node, 
                templateId, 
                newVmId, 
                name, 
                description, 
                fullClone ? 1 : 0, 
                pool, 
                snapname, 
                storage, 
                targetNode, 
                ticket, 
                ticketManager.getCsrfToken()
            );
            
        } catch (Exception e) {
            LOG.error("Failed to clone VM {} to {}", templateId, newVmId, e);
            throw new RuntimeException("Failed to clone VM: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a VM is a template.
     * Templates have special properties and cannot be started.
     * 
     * @param vmId VM ID to check
     * @param ticket Authentication ticket
     * @return true if the VM is a template, false otherwise
     * @throws RuntimeException if VM not found
     */
    public boolean isTemplate(int vmId, @AuthTicket String ticket) {
        List<VMResponse> vms = listVMs(ticket);
        
        VMResponse vm = vms.stream()
            .filter(v -> v.vmid() == vmId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
        return vm.template() == 1;
    }
    
    /**
     * Get all templates in the cluster.
     * Templates are VMs that have been converted to templates and cannot be started.
     * 
     * @param ticket Authentication ticket
     * @return List of VMs that are templates
     */
    public List<VMResponse> getTemplates(@AuthTicket String ticket) {
        return listVMs(ticket).stream()
            .filter(vm -> vm.template() == 1)
            .collect(Collectors.toList());
    }
    
    /**
     * Create a VM from a cloud-init image with full configuration.
     * This method orchestrates the entire cloud-init VM creation process including:
     * - Creating the VM container
     * - Importing the disk image
     * - Configuring cloud-init settings
     * - Optionally starting the VM
     * 
     * @param request CloudInitVMRequest with all VM configuration
     * @param ticket Authentication ticket  
     * @return CreateVMResponse with the created VM details
     */
    public CreateVMResponse createCloudInitVM(CloudInitVMRequest request, @AuthTicket String ticket) {
        // Allocate VM ID if not provided
        Integer vmId = request.vmid();
        if (vmId == null) {
            vmId = vmIdService.getNextAvailableVmId(ticket);
            LOG.info("Allocated VM ID {} for VM {}", vmId, request.name());
        }
        
        // Determine creation node - use templateNode if specified, otherwise use target node
        String creationNode = request.templateNode() != null ? request.templateNode() : request.node();
        String targetNode = request.node();
        
        LOG.info("Creating cloud-init VM {} (ID: {}) from image {} on node {}", 
                 request.name(), vmId, request.imageSource(), creationNode);
        
        // Build CreateVMRequest WITHOUT main disk (following Ansible pattern)
        CreateVMRequestBuilder builder = CreateVMRequestBuilder.builder()
            .vmid(vmId)
            .name(request.name())
            .cores(request.cores())
            .memory(request.memoryMB())
            .cpu(request.cpuType() != null && !request.cpuType().isEmpty() ? 
                 request.cpuType() : "x86-64-v2-AES")
            .vga(request.vgaType() != null ? request.vgaType() : "std");
        
        // Handle firmware configuration
        if (request.firmware() != null) {
            applyFirmwareConfiguration(builder, request.firmware());
        } else {
            // Default to SeaBIOS for backward compatibility
            builder.machine("pc").bios("seabios");
        }
        
        // Handle SCSI hardware configuration
        if (request.scsihw() != null) {
            builder.scsihw(request.scsihw());
        }
        
        // Handle serial console configuration
        if (request.serial0() != null) {
            builder.serial0(request.serial0());
        }
        
        // Handle multiple networks
        List<com.coffeesprout.api.dto.NetworkConfig> networks = request.networks();
        
        // Backward compatibility: if networks is null/empty but network is set, convert it
        if ((networks == null || networks.isEmpty()) && request.network() != null) {
            CloudInitVMRequest.NetworkConfig oldNet = request.network();
            networks = List.of(new com.coffeesprout.api.dto.NetworkConfig(
                "virtio", 
                oldNet.bridge(), 
                oldNet.vlanTag(),
                null, null, null, null, null, null
            ));
        }
        
        // Set up networks
        if (networks != null) {
            for (com.coffeesprout.api.dto.NetworkConfig net : networks) {
                String model = net.model() != null ? net.model() : "virtio";
                Integer vlan = net.vlan();
                String tag = net.toProxmoxString().replaceFirst("^[^,]+,bridge=[^,]+", "")
                    .replaceFirst("^,", "");
                builder.addNetwork(model, net.bridge(), vlan, tag.isEmpty() ? null : tag);
            }
        }
        
        // QEMU agent
        if (request.qemuAgent() != null && request.qemuAgent()) {
            builder.agent(true);
        }
        
        // Description
        if (request.description() != null) {
            builder.description(request.description());
        }
        
        // Tags
        if (request.tags() != null) {
            builder.tags(request.tags());
        }
        
        // Set cloud-init parameters during VM creation
        if (request.cloudInitUser() != null) {
            builder.ciuser(request.cloudInitUser());
        }
        
        // Handle IP configurations
        List<String> ipConfigs = request.ipConfigs();
        
        // Backward compatibility: if ipConfigs is null/empty but ipConfig is set, convert it
        if ((ipConfigs == null || ipConfigs.isEmpty()) && request.ipConfig() != null) {
            ipConfigs = List.of(request.ipConfig());
        }
        
        // Set up IP configurations
        if (ipConfigs != null) {
            for (String ipConfig : ipConfigs) {
                builder.addIpConfig(ipConfig);
            }
        }
        
        if (request.nameservers() != null) {
            builder.nameserver(request.nameservers());
        }
        
        if (request.searchDomain() != null) {
            builder.searchdomain(request.searchDomain());
        }
        
        if (request.sshKeys() != null) {
            // Pass SSH keys as-is - createVM will handle proper encoding
            LOG.info("Setting SSH keys during VM creation (length: {})", request.sshKeys().length());
            builder.sshkeys(request.sshKeys());
        }
        
        // Build the final request
        CreateVMRequest clientRequest = builder.build();
        
        // Create the VM without main disk
        LOG.info("Creating VM {} without main disk", vmId);
        CreateVMResponse response = createVM(creationNode, clientRequest, ticket);
        
        // Now import and attach the disk as scsi0
        try {
            LOG.info("Importing disk from {} to VM {}", request.imageSource(), vmId);
            
            // Build disk config with import-from
            DiskConfig importDisk = request.toDiskConfig();
            String diskString = importDisk.toProxmoxString();
            LOG.info("Disk import config: {}", diskString);
            
            // Use the updateDisk method to import and attach the disk
            importDisk(creationNode, vmId, diskString, ticket);
            
            // Resize the disk if needed
            if (request.diskSizeGB() > 0) {
                LOG.info("Resizing disk scsi0 to {}G for VM {}", request.diskSizeGB(), vmId);
                resizeDisk(creationNode, vmId, "scsi0", request.diskSizeGB() + "G", ticket);
            }
            
            // Update boot order to boot from scsi0
            CreateVMRequest bootOrderUpdate = CreateVMRequestBuilder.builder()
                .vmid(vmId)
                .name("temp")
                .boot("order=scsi0")
                .build();
            updateVMConfig(creationNode, vmId, bootOrderUpdate, ticket);
            
        } catch (Exception e) {
            LOG.error("Failed to import disk for VM {}, cleaning up", vmId, e);
            // Clean up the VM if disk import fails
            try {
                deleteVM(creationNode, vmId, ticket);
            } catch (Exception cleanupEx) {
                LOG.error("Failed to clean up VM {} after disk import failure", vmId, cleanupEx);
            }
            throw new RuntimeException("Failed to import disk: " + e.getMessage(), e);
        }
        
        // Cloud-init settings are now configured during VM creation
        // Only update password if it was provided (can't be set during creation)
        if (request.cloudInitPassword() != null) {
            LOG.info("Setting cloud-init password for VM {}", vmId);
            CreateVMRequest passwordConfig = CreateVMRequestBuilder.builder()
                .vmid(vmId)
                .name("temp")
                .cipassword(request.cloudInitPassword())
                .build();
            updateVMConfig(creationNode, vmId, passwordConfig, ticket);
        }
        
        // Migrate VM if created on different node than target
        if (!creationNode.equals(targetNode)) {
            LOG.info("Migrating VM {} from '{}' to target node '{}'", vmId, creationNode, targetNode);
            try {
                com.coffeesprout.api.dto.MigrationRequest migrationRequest = new com.coffeesprout.api.dto.MigrationRequest(
                    targetNode,
                    true,  // allowOfflineMigration
                    true,  // withLocalDisks
                    false, // force
                    null,  // bwlimit
                    null,  // targetStorage
                    null,  // migrationType
                    null   // migrationNetwork
                );
                
                migrationService.migrateVM(vmId, migrationRequest, ticket);
                LOG.info("Successfully migrated VM {} to '{}'", vmId, targetNode);
                
                // Update creationNode to targetNode for subsequent operations
                creationNode = targetNode;
            } catch (Exception e) {
                LOG.error("Failed to migrate VM {} to '{}': {}", vmId, targetNode, e.getMessage());
                // Migration failed but VM exists - continue with warning
                LOG.warn("VM {} created on '{}' but migration to '{}' failed", vmId, creationNode, targetNode);
            }
        }
        
        // Start VM if requested
        if (request.start() != null && request.start()) {
            LOG.info("Starting VM {}", vmId);
            startVM(creationNode, vmId, ticket);
        }

        // Trigger Ansible callback if enabled
        try {
            // Build VM response for callback
            VMResponse vmResponse = new VMResponse(
                vmId,
                request.name(),
                creationNode,
                request.start() != null && request.start() ? "running" : "stopped",
                request.cores(),
                (long)request.memoryMB() * 1024 * 1024, // Convert MB to bytes
                0L, // maxdisk - not available during creation
                0L, // uptime - 0 for new VM
                "qemu",
                request.tags() != null ? List.of(request.tags().split(",")) : List.of(),
                null, // pool
                0 // template - regular VM
            );
            ansibleCallbackService.triggerPostCreationCallback(vmResponse, null);
        } catch (Exception e) {
            LOG.error("Failed to trigger Ansible callback for VM {}, but VM creation succeeded", vmId, e);
            // Don't fail the VM creation if callback fails
        }

        return response;
    }
    
    /**
     * Apply firmware configuration to the VM request builder
     */
    private void applyFirmwareConfiguration(CreateVMRequestBuilder builder, FirmwareConfig firmware) {
        // Validate firmware configuration
        firmware.validate();
        
        LOG.info("Applying firmware configuration: {} with machine type {}", 
                 firmware.type(), firmware.machine());
        
        // Set machine type and BIOS
        builder.machine(firmware.machine().getValue())
               .bios(firmware.type().getProxmoxValue());
        
        // Configure EFI disk for UEFI
        if (firmware.type() == FirmwareConfig.FirmwareType.UEFI && firmware.efidisk() != null) {
            String efidiskConfig = firmware.efidisk().toProxmoxString();
            LOG.info("Setting EFI disk configuration: {}", efidiskConfig);
            builder.efidisk0(efidiskConfig);
        }
    }
    
    /**
     * Create EFI disk after VM creation (for UEFI VMs)
     * Note: In Proxmox, EFI disk is usually created during VM creation via efidisk0 parameter,
     * but this method is available for post-creation setup if needed.
     */
    private void createEFIDisk(String node, int vmId, FirmwareConfig.EFIDiskConfig efidiskConfig, @AuthTicket String ticket) {
        if (efidiskConfig == null) {
            return;
        }
        
        LOG.info("Creating EFI disk for VM {} on node {}", vmId, node);
        
        try {
            // Create EFI disk using updateVMConfig
            CreateVMRequest efiDiskRequest = CreateVMRequestBuilder.builder()
                .vmid(vmId)
                .name("temp")
                .efidisk0(efidiskConfig.toProxmoxString())
                .build();
                
            updateVMConfig(node, vmId, efiDiskRequest, ticket);
            LOG.info("Successfully created EFI disk for VM {}", vmId);
            
        } catch (Exception e) {
            LOG.error("Failed to create EFI disk for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to create EFI disk: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate and get default firmware configuration
     */
    public static FirmwareConfig getDefaultFirmwareConfig(String targetStorage, boolean useUEFI) {
        if (useUEFI) {
            return FirmwareConfig.defaultUEFI(targetStorage);
        } else {
            return FirmwareConfig.defaultSeaBIOS();
        }
    }
}