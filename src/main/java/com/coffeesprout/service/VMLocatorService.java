package com.coffeesprout.service;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.service.AutoAuthenticate;
import com.coffeesprout.service.AuthTicket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service to locate VMs across nodes in the cluster.
 * Centralizes VM lookup logic to avoid duplication across services.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMLocatorService {
    
    private static final Logger log = LoggerFactory.getLogger(VMLocatorService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    NodeService nodeService;
    
    // Cache for VM locations (VM ID -> Node name)
    // This is cleared periodically or on certain operations
    private final Map<Integer, String> vmLocationCache = new ConcurrentHashMap<>();
    
    /**
     * Find which node a VM is on.
     * 
     * @param vmId The VM ID to locate
     * @param ticket Authentication ticket
     * @return The node name where the VM is located
     * @throws ProxmoxException if VM not found
     */
    public String findNodeForVM(int vmId, @AuthTicket String ticket) {
        // Check cache first
        String cachedNode = vmLocationCache.get(vmId);
        if (cachedNode != null) {
            log.debug("Found VM {} on node {} (cached)", vmId, cachedNode);
            return cachedNode;
        }
        
        // Search across all VMs
        try {
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Optional<VMResponse> vm = allVMs.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst();
                
            if (vm.isPresent()) {
                String node = vm.get().node();
                log.debug("Found VM {} on node {}", vmId, node);
                vmLocationCache.put(vmId, node);
                return node;
            }
        } catch (Exception e) {
            log.error("Error searching for VM {}: {}", vmId, e.getMessage());
        }
        
        throw ProxmoxException.notFound("VM", String.valueOf(vmId));
    }
    
    /**
     * Find a VM by ID across all nodes.
     * 
     * @param vmId The VM ID to find
     * @param ticket Authentication ticket
     * @return The VM response if found
     * @throws ProxmoxException if VM not found
     */
    public VMResponse findVM(int vmId, @AuthTicket String ticket) {
        // First try to find the VM in the list
        try {
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Optional<VMResponse> vm = allVMs.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst();
                
            if (vm.isPresent()) {
                // Update cache with node location
                vmLocationCache.put(vmId, vm.get().node());
                return vm.get();
            }
        } catch (Exception e) {
            log.error("Failed to find VM {}: {}", vmId, e.getMessage());
        }
        
        throw ProxmoxException.notFound("VM", String.valueOf(vmId));
    }
    
    /**
     * Find multiple VMs by IDs.
     * 
     * @param vmIds List of VM IDs to find
     * @param ticket Authentication ticket
     * @return Map of VM ID to VM response
     */
    public Map<Integer, VMResponse> findVMs(List<Integer> vmIds, @AuthTicket String ticket) {
        Map<Integer, VMResponse> result = new HashMap<>();
        Set<Integer> vmIdSet = new HashSet<>(vmIds);
        
        try {
            // Get all VMs and filter by the requested IDs
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            
            for (VMResponse vm : allVMs) {
                if (vmIdSet.contains(vm.vmid())) {
                    result.put(vm.vmid(), vm);
                    // Update cache
                    vmLocationCache.put(vm.vmid(), vm.node());
                }
            }
        } catch (Exception e) {
            log.error("Failed to find VMs: {}", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Find nodes for multiple VMs.
     * 
     * @param vmIds List of VM IDs
     * @param ticket Authentication ticket
     * @return Map of VM ID to node name
     */
    public Map<Integer, String> findNodesForVMs(List<Integer> vmIds, @AuthTicket String ticket) {
        Map<Integer, String> result = new HashMap<>();
        List<Integer> uncachedVmIds = new ArrayList<>();
        
        // Check cache first
        for (Integer vmId : vmIds) {
            String cachedNode = vmLocationCache.get(vmId);
            if (cachedNode != null) {
                result.put(vmId, cachedNode);
            } else {
                uncachedVmIds.add(vmId);
            }
        }
        
        // Search for uncached VMs
        if (!uncachedVmIds.isEmpty()) {
            Map<Integer, VMResponse> foundVms = findVMs(uncachedVmIds, ticket);
            for (Map.Entry<Integer, VMResponse> entry : foundVms.entrySet()) {
                result.put(entry.getKey(), entry.getValue().node());
            }
        }
        
        return result;
    }
    
    /**
     * Clear the VM location cache.
     * Should be called after migrations or significant changes.
     */
    public void clearCache() {
        log.info("Clearing VM location cache");
        vmLocationCache.clear();
    }
    
    /**
     * Remove specific VM from cache.
     * 
     * @param vmId VM ID to remove from cache
     */
    public void evictFromCache(int vmId) {
        vmLocationCache.remove(vmId);
    }
    
    /**
     * Update cache entry for a VM.
     * 
     * @param vmId VM ID
     * @param node New node location
     */
    public void updateCache(int vmId, String node) {
        if (node != null) {
            vmLocationCache.put(vmId, node);
        } else {
            vmLocationCache.remove(vmId);
        }
    }
}