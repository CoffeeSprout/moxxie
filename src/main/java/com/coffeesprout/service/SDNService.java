package com.coffeesprout.service;

import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.*;
import com.coffeesprout.config.MoxxieConfig;
import com.coffeesprout.config.SDNConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
@AutoAuthenticate
public class SDNService {
    
    private static final Logger LOG = Logger.getLogger(SDNService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    MoxxieConfig config;
    
    @Inject
    SDNConfig sdnConfig;
    
    // VLAN allocation tracking (in-memory for now, will be persisted later)
    private final Map<String, Integer> clientVlanMap = new ConcurrentHashMap<>();
    private final Set<Integer> allocatedVlans = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Pattern for valid VNet IDs (alphanumeric and hyphens)
    private static final Pattern VNET_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*[a-z0-9]$");
    
    /**
     * Create or retrieve a VNet for a client/project combination
     */
    public VNet createClientVNet(String clientId, String projectName, @AuthTicket String ticket) {
        if (!sdnConfig.enabled()) {
            throw new IllegalStateException("SDN functionality is not enabled");
        }
        
        // Allocate or retrieve VLAN for client
        int vlanTag = getOrAllocateVlan(clientId);
        
        // Create VNet ID based on naming pattern
        String vnetId = generateVNetId(clientId, projectName);
        
        LOG.infof("Creating VNet %s for client %s with VLAN %d", vnetId, clientId, vlanTag);
        
        // Create in Proxmox SDN
        return createVNet(vnetId, vlanTag, clientId, ticket);
    }
    
    /**
     * Create a VNet with explicit VLAN tag
     */
    public VNet createVNetWithVlan(String clientId, String projectName, int vlanTag, @AuthTicket String ticket) {
        if (!sdnConfig.enabled()) {
            throw new IllegalStateException("SDN functionality is not enabled");
        }
        
        // Validate VLAN tag is in allowed range
        if (vlanTag < 1 || vlanTag > 4094) {
            throw new IllegalArgumentException("VLAN tag must be between 1 and 4094");
        }
        
        // Create VNet ID based on naming pattern
        String vnetId = generateVNetId(clientId, projectName);
        
        LOG.infof("Creating VNet %s for client %s with explicit VLAN %d", vnetId, clientId, vlanTag);
        
        // Mark VLAN as allocated
        allocatedVlans.add(vlanTag);
        clientVlanMap.put(clientId, vlanTag);
        
        // Create in Proxmox SDN
        return createVNet(vnetId, vlanTag, clientId, ticket);
    }
    
    /**
     * Get or allocate a VLAN for a client
     */
    public int getOrAllocateVlan(String clientId) {
        return clientVlanMap.computeIfAbsent(clientId, k -> {
            // Find next available VLAN in configured range
            for (int vlan = sdnConfig.vlanRangeStart(); vlan <= sdnConfig.vlanRangeEnd(); vlan++) {
                if (allocatedVlans.add(vlan)) {
                    LOG.infof("Allocated VLAN %d to client %s", vlan, clientId);
                    return vlan;
                }
            }
            throw ProxmoxException.resourceLimitExceeded(
                "VLAN",
                allocatedVlans.size(),
                sdnConfig.vlanRangeEnd() - sdnConfig.vlanRangeStart() + 1
            ).withDetail("vlanRangeStart", String.valueOf(sdnConfig.vlanRangeStart()))
             .withDetail("vlanRangeEnd", String.valueOf(sdnConfig.vlanRangeEnd()));
        });
    }
    
    /**
     * Generate a VNet ID based on the naming pattern
     */
    private String generateVNetId(String clientId, String projectName) {
        String vnetId = sdnConfig.vnetNamingPattern()
            .replace("{client}", clientId)
            .replace("{project}", projectName != null ? projectName : "default");
        
        // Sanitize the VNet ID
        return sanitizeVNetId(vnetId);
    }
    
    /**
     * Sanitize a VNet ID to ensure it's valid for Proxmox
     */
    private String sanitizeVNetId(String vnetId) {
        // Convert to lowercase and replace invalid chars with hyphens
        String sanitized = vnetId.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-+|-+$", "");
        
        // Ensure it starts and ends with alphanumeric
        if (sanitized.isEmpty()) {
            sanitized = "vnet";
        }
        
        // Limit length to 50 chars (Proxmox limit)
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        // Remove trailing hyphens after truncation
        sanitized = sanitized.replaceAll("-+$", "");
        
        return sanitized;
    }
    
    /**
     * Create a VNet in Proxmox SDN
     */
    private VNet createVNet(String vnetId, int vlanTag, String alias, String ticket) {
        try {
            CreateVNetResponse response = proxmoxClient.createVNet(
                vnetId,
                sdnConfig.defaultZone(),
                vlanTag,
                alias,
                ticket,
                ticketManager.getCsrfToken()
            );
            
            LOG.infof("Created VNet %s with response: %s", vnetId, response.getData());
            
            // Apply SDN configuration if enabled
            if (sdnConfig.applyOnChange()) {
                applySDNConfiguration(ticket);
            }
            
            // Create and return VNet object
            VNet vnet = new VNet();
            vnet.setVnet(vnetId);
            vnet.setZone(sdnConfig.defaultZone());
            vnet.setTag(vlanTag);
            vnet.setAlias(alias);
            
            return vnet;
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create VNet %s", vnetId);
            throw ProxmoxException.internalError("create VNet", e);
        }
    }
    
    /**
     * Apply SDN configuration changes
     */
    public void applySDNConfiguration(@AuthTicket String ticket) {
        try {
            ApplySDNResponse response = proxmoxClient.applySDNConfig(
                ticket,
                ticketManager.getCsrfToken()
            );
            
            LOG.infof("Applied SDN configuration: %s", response.getData());
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to apply SDN configuration");
            throw ProxmoxException.internalError("apply SDN configuration", e);
        }
    }
    
    /**
     * List all SDN zones
     */
    public NetworkZonesResponse listZones(@AuthTicket String ticket) {
        try {
            return proxmoxClient.listSDNZones(ticket);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list SDN zones");
            throw ProxmoxException.internalError("list SDN zones", e);
        }
    }
    
    /**
     * List all VNets
     */
    public VNetsResponse listVNets(String zone, @AuthTicket String ticket) {
        try {
            return proxmoxClient.listVNets(zone, ticket);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list VNets");
            throw ProxmoxException.internalError("list VNets", e);
        }
    }
    
    /**
     * Delete a VNet
     */
    public void deleteVNet(String vnetId, @AuthTicket String ticket) {
        try {
            DeleteResponse response = proxmoxClient.deleteVNet(
                vnetId,
                ticket,
                ticketManager.getCsrfToken()
            );
            
            LOG.infof("Deleted VNet %s: %s", vnetId, response.getData());
            
            // Apply SDN configuration if enabled
            if (sdnConfig.applyOnChange()) {
                applySDNConfiguration(ticket);
            }
            
        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete VNet %s", vnetId);
            throw ProxmoxException.internalError("delete VNet", e);
        }
    }
    
    /**
     * Ensure a VNet exists for a client/project, creating if necessary
     */
    public String ensureClientVNet(String clientId, String projectName, @AuthTicket String ticket) {
        if (!sdnConfig.enabled()) {
            return null; // SDN not enabled, return null
        }
        
        String vnetId = generateVNetId(clientId, projectName);
        
        // Check if VNet already exists
        try {
            VNetsResponse vnets = listVNets(sdnConfig.defaultZone(), ticket);
            boolean exists = vnets.getData().stream()
                .anyMatch(vnet -> vnet.getVnet().equals(vnetId));
            
            if (!exists && sdnConfig.autoCreateVnets()) {
                createClientVNet(clientId, projectName, ticket);
            }
            
            return vnetId;
            
        } catch (Exception e) {
            LOG.warnf("Failed to check/create VNet for client %s: %s", clientId, e.getMessage());
            if (sdnConfig.autoCreateVnets()) {
                // Try to create anyway
                createClientVNet(clientId, projectName, ticket);
                return vnetId;
            }
            return null;
        }
    }
    
    /**
     * Initialize allocated VLANs from persistence (placeholder for now)
     */
    public void initializeAllocations() {
        // TODO: Load from database
        LOG.info("Initializing SDN allocations (in-memory for now)");
    }
    
}