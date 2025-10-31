package com.coffeesprout.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.dto.ClusterNextIdResponse;
import com.coffeesprout.util.UnitConverter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Service for managing VM ID allocation in the Proxmox cluster.
 * Provides automatic VM ID generation with fallback mechanisms.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMIdService {

    private static final Logger LOG = Logger.getLogger(VMIdService.class);

    // VM ID constraints
    private static final int MIN_VM_ID = 100;
    private static final int MAX_VM_ID = 999999999;
    private static final int RANDOM_MAX = 1000000; // Reasonable upper bound for random generation

    // Retry configuration
    private static final int MAX_RETRIES = 3;

    // Track recently allocated VM IDs to avoid conflicts
    private final ConcurrentHashMap<Integer, Long> recentlyAllocatedIds = new ConcurrentHashMap<>();
    private static final long ALLOCATION_TIMEOUT_MS = 60000; // 1 minute

    // Last allocated VM ID for incremental allocation
    private final AtomicInteger lastAllocatedId = new AtomicInteger(0);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    /**
     * Get the next available VM ID using Proxmox cluster API.
     * Falls back to random generation if the API call fails.
     *
     * @return Next available VM ID
     */
    public synchronized int getNextAvailableVmId(@AuthTicket String ticket) {
        // Clean up old allocations
        cleanupOldAllocations();

        // Keep trying to get next VM ID from Proxmox until we get an unused one
        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            try {
                ClusterNextIdResponse response = proxmoxClient.getNextVmId(ticket);
                if (response != null && response.data() != null) {
                    int vmId = response.data();
                    if (isValidVmId(vmId) && !isRecentlyAllocated(vmId)) {
                        // Track this allocation
                        recentlyAllocatedIds.put(vmId, System.currentTimeMillis());
                        lastAllocatedId.set(vmId);
                        LOG.infof("Allocated VM ID from Proxmox cluster API: %d (attempt %d)", vmId, attempts + 1);
                        return vmId;
                    } else {
                        LOG.debugf("VM ID %d is recently allocated or invalid, trying again", vmId);
                    }
                }
            } catch (Exception e) {
                LOG.warnf("Failed to get next VM ID from Proxmox API (attempt %d): %s", attempts + 1, e.getMessage());
            }

            attempts++;

            // Small delay between attempts
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Fallback to generation if Proxmox API fails after all attempts
        LOG.warnf("Failed to get unique VM ID from Proxmox after %d attempts, falling back to generation", maxAttempts);
        int vmId = generateUniqueVmId(0);

        // Track this allocation
        recentlyAllocatedIds.put(vmId, System.currentTimeMillis());
        LOG.infof("Allocated VM ID (fallback): %d", vmId);

        return vmId;
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
            LOG.debugf("Generated random VM ID (attempt %d): %d", attempt + 1, vmId);
            return vmId; // In the future, we could add availability checking here
        }

        // Final fallback
        int vmId = generateRandomVmId();
        LOG.warnf("Used final fallback VM ID after %d attempts: %d", MAX_RETRIES, vmId);
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

    /**
     * Generate a unique VM ID based on base ID or incremental allocation.
     *
     * @param baseId Base ID from Proxmox API (0 if not available)
     * @return Unique VM ID
     */
    private int generateUniqueVmId(int baseId) {
        // Use baseId if provided, otherwise use lastAllocated + 1 or MIN_VM_ID
        int startId = baseId > 0 ? baseId : Math.max(lastAllocatedId.get() + 1, MIN_VM_ID);

        // Try incremental allocation first
        for (int i = 0; i < UnitConverter.Time.MILLIS_PER_SECOND; i++) {
            int candidateId = startId + i;
            if (isValidVmId(candidateId) && !isRecentlyAllocated(candidateId)) {
                lastAllocatedId.set(candidateId);
                return candidateId;
            }
        }

        // Fall back to random generation
        for (int attempt = 0; attempt < MAX_RETRIES * 10; attempt++) {
            int candidateId = generateRandomVmId();
            if (!isRecentlyAllocated(candidateId)) {
                lastAllocatedId.updateAndGet(current -> Math.max(current, candidateId));
                return candidateId;
            }
        }

        // Emergency fallback - increment from last known
        int fallbackId = lastAllocatedId.incrementAndGet();
        LOG.warnf("Using emergency fallback VM ID: %d", fallbackId);
        return fallbackId;
    }

    /**
     * Check if a VM ID was recently allocated.
     *
     * @param vmId VM ID to check
     * @return true if recently allocated
     */
    private boolean isRecentlyAllocated(int vmId) {
        Long allocationTime = recentlyAllocatedIds.get(vmId);
        return allocationTime != null &&
               (System.currentTimeMillis() - allocationTime) < ALLOCATION_TIMEOUT_MS;
    }

    /**
     * Clean up old VM ID allocations.
     */
    private void cleanupOldAllocations() {
        long now = System.currentTimeMillis();
        recentlyAllocatedIds.entrySet().removeIf(entry ->
            (now - entry.getValue()) > ALLOCATION_TIMEOUT_MS
        );
    }
}
