package com.coffeesprout.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for locating VMs across Proxmox cluster nodes.
 * Centralizes the logic for finding which node a VM is running on.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMLocatorService {

    private static final Logger LOG = LoggerFactory.getLogger(VMLocatorService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    TicketManager ticketManager;

    @Inject
    VMInventoryService vmInventoryService;

    /**
     * Find which node a VM is running on.
     *
     * @param vmId The VM ID to locate
     * @param ticket Authentication ticket
     * @return The node name where the VM is located, or empty if not found
     */
    public Optional<String> findNodeForVM(int vmId, @AuthTicket String ticket) {
        try {
            // First, try using cluster resources API (most efficient)
            var resources = proxmoxClient.getClusterResources(
                ticket,
                ticketManager.getCsrfToken(),
                "vm"
            );

            if (resources != null && resources.has("data")) {
                var dataArray = resources.get("data");
                for (var resource : dataArray) {
                    if (resource.has("vmid") && resource.get("vmid").asInt() == vmId
                            && resource.has("node")) {
                        String node = resource.get("node").asText();
                        LOG.debug("Found VM {} on node {} via cluster resources", vmId, node);
                        return Optional.of(node);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error finding VM {} via cluster resources: {}", vmId, e.getMessage());
        }

        // Fallback: try using VM service (less efficient but more reliable)
        try {
            return vmInventoryService.listAll(ticket).stream()
                .filter(vm -> vm.vmid() == vmId)
                .map(VMResponse::node)
                .findFirst();
        } catch (Exception e) {
            LOG.error("Error finding VM {} via VM service: {}", vmId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Find a VM by ID and return its full details.
     *
     * @param vmId The VM ID to find
     * @param ticket Authentication ticket
     * @return The VM details, or empty if not found
     */
    public Optional<VMResponse> findVM(int vmId, @AuthTicket String ticket) {
        try {
            return vmInventoryService.listAll(ticket).stream()
                .filter(vm -> vm.vmid() == vmId)
                .findFirst();
        } catch (Exception e) {
            LOG.error("Error finding VM {}: {}", vmId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if a VM exists in the cluster.
     *
     * @param vmId The VM ID to check
     * @param ticket Authentication ticket
     * @return true if the VM exists, false otherwise
     */
    public boolean vmExists(int vmId, @AuthTicket String ticket) {
        return findNodeForVM(vmId, ticket).isPresent();
    }

    /**
     * Get VM details directly from a specific node.
     * This is more efficient if you already know which node the VM is on.
     *
     * @param node The node name
     * @param vmId The VM ID
     * @param ticket Authentication ticket
     * @return The VM configuration from Proxmox
     */
    public Optional<JsonNode> getVMConfig(String node, int vmId, @AuthTicket String ticket) {
        try {
            var config = proxmoxClient.getVMConfig(
                node,
                vmId,
                ticket,
                ticketManager.getCsrfToken()
            );

            if (config != null && config.has("data")) {
                return Optional.of(config.get("data"));
            }
        } catch (Exception e) {
            LOG.error("Error getting config for VM {} on node {}: {}", vmId, node, e.getMessage());
        }
        return Optional.empty();
    }
}
