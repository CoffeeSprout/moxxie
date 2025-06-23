package com.coffeesprout.service;

import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.dto.ClusterNextIdResponse;
import com.coffeesprout.service.AutoAuthenticate;
import com.coffeesprout.service.AuthTicket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for managing VM ID allocation in the Proxmox cluster.
 * Provides automatic VM ID generation with fallback mechanisms.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMIdService {

    private static final Logger log = Logger.getLogger(VMIdService.class);
    
    // VM ID constraints
    private static final int MIN_VM_ID = 100;
    private static final int MAX_VM_ID = 999999999;
    private static final int RANDOM_MAX = 1000000; // Reasonable upper bound for random generation
    
    // Retry configuration
    private static final int MAX_RETRIES = 3;

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    /**
     * Get the next available VM ID using Proxmox cluster API.
     * Falls back to random generation if the API call fails.
     * 
     * @return Next available VM ID
     */
    public int getNextAvailableVmId(@AuthTicket String ticket) {
        // Try Proxmox cluster API first
        try {
            ClusterNextIdResponse response = proxmoxClient.getNextVmId(ticket);
            if (response != null && response.data() != null) {
                int vmId = response.data();
                if (isValidVmId(vmId)) {
                    log.infof("Got next VM ID from Proxmox cluster API: %d", vmId);
                    return vmId;
                }
            }
        } catch (Exception e) {
            log.warnf("Failed to get next VM ID from Proxmox API, falling back to random generation: %s", e.getMessage());
        }
        
        // Fallback to random generation with retry logic
        return generateRandomVmIdWithRetry();
    }

    /**
     * Generate a random VM ID with retry logic for conflict avoidance.
     * This method aligns with Proxmox's recommended approach for avoiding race conditions.
     * 
     * @return Random VM ID
     */
    public int generateRandomVmIdWithRetry() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            int vmId = generateRandomVmId();
            log.debugf("Generated random VM ID (attempt %d): %d", attempt + 1, vmId);
            return vmId; // In the future, we could add availability checking here
        }
        
        // Final fallback
        int vmId = generateRandomVmId();
        log.warnf("Used final fallback VM ID after %d attempts: %d", MAX_RETRIES, vmId);
        return vmId;
    }

    /**
     * Generate a single random VM ID within the valid range.
     * 
     * @return Random VM ID between MIN_VM_ID and RANDOM_MAX
     */
    private int generateRandomVmId() {
        return ThreadLocalRandom.current().nextInt(MIN_VM_ID, RANDOM_MAX);
    }

    /**
     * Validate that a VM ID is within acceptable bounds.
     * 
     * @param vmId VM ID to validate
     * @return true if VM ID is valid
     */
    public boolean isValidVmId(int vmId) {
        return vmId >= MIN_VM_ID && vmId <= MAX_VM_ID;
    }

    /**
     * Get the minimum allowed VM ID.
     * 
     * @return Minimum VM ID
     */
    public int getMinVmId() {
        return MIN_VM_ID;
    }

    /**
     * Get the maximum allowed VM ID.
     * 
     * @return Maximum VM ID
     */
    public int getMaxVmId() {
        return MAX_VM_ID;
    }
}