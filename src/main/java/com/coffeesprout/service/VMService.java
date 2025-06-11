package com.coffeesprout.service;

import com.coffeesprout.client.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@AutoAuthenticate
public class VMService {

    private static final Logger log = LoggerFactory.getLogger(VMService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;

    @SafeMode(value = false)  // Read operation
    public List<VM> listVMs(String ticket) {
        VMsResponse response = proxmoxClient.getVMs(ticket);
        return response.getData();
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public CreateVMResponse createVM(String node, CreateVMRequest request, String ticket) {
        return proxmoxClient.createVM(node, ticket, ticketManager.getCsrfToken(), request);
    }

    public ImportImageResponse importImage(String node, String storage, ImportImageRequest request, String ticket) {
        return proxmoxClient.importCloudImage(node, storage, ticket, request);
    }

    @SafeMode(value = false)  // Read operation
    public VMStatusResponse getVMStatus(String node, int vmid, String ticket) {
        log.debug("Getting detailed status for VM {} on node {}", vmid, node);
        return proxmoxClient.getVMStatus(node, vmid, ticket);
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void startVM(String node, int vmid, String ticket) {
        log.info("Starting VM {} on node {}", vmid, node);
        proxmoxClient.startVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }

    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void stopVM(String node, int vmid, String ticket) {
        log.info("Stopping VM {} on node {}", vmid, node);
        proxmoxClient.stopVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }

    @SafeMode(operation = SafeMode.Operation.DELETE)
    public void deleteVM(String node, int vmid, String ticket) {
        log.info("Deleting VM {} on node {}", vmid, node);
        proxmoxClient.deleteVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void rebootVM(String node, int vmid, String ticket) {
        log.info("Rebooting VM {} on node {}", vmid, node);
        proxmoxClient.rebootVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void suspendVM(String node, int vmid, String ticket) {
        log.info("Suspending VM {} on node {}", vmid, node);
        proxmoxClient.suspendVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void resumeVM(String node, int vmid, String ticket) {
        log.info("Resuming VM {} on node {}", vmid, node);
        proxmoxClient.resumeVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public void shutdownVM(String node, int vmid, String ticket) {
        log.info("Shutting down VM {} on node {}", vmid, node);
        proxmoxClient.shutdownVM(node, vmid, ticket, ticketManager.getCsrfToken());
    }
    
    @SafeMode(value = false)  // Read operation
    public Map<String, Object> getVMConfig(String node, int vmId, String ticket) {
        log.debug("Getting VM config for VM {} on node '{}'", vmId, node);
        VMConfigResponse response = proxmoxClient.getVMConfig(node, vmId, ticket);
        return response.getData() != null ? response.getData() : new HashMap<>();
    }
}