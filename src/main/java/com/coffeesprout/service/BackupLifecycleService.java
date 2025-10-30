package com.coffeesprout.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.Node;
import com.coffeesprout.client.ProxmoxClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class BackupLifecycleService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupLifecycleService.class);

    @Inject
    BackupService backupService;

    @Inject
    VMService vmService;

    @Inject
    NodeService nodeService;

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    TicketManager ticketManager;

    /**
     * Get backups eligible for deletion based on retention policy
     */
    public List<BackupDeletionCandidate> getRetentionCandidates(String retentionPolicy, List<String> tags,
                                                                List<Integer> vmIds, boolean includeProtected,
                                                                @AuthTicket String ticket) {
        LOG.debug("Finding retention candidates with policy: {}", retentionPolicy);

        try {
            // Parse retention policy
            RetentionPolicy policy = parseRetentionPolicy(retentionPolicy);

            // Get all backups
            List<BackupResponse> allBackups = backupService.listAllBackups(ticket);

            // Get VM information for names and tags
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Map<Integer, VMResponse> vmMap = allVMs.stream()
                    .collect(Collectors.toMap(VMResponse::vmid, vm -> vm));

            // Filter backups by VM criteria
            List<BackupResponse> filteredBackups = filterBackupsByVMCriteria(allBackups, vmMap, tags, vmIds);

            // Group by VM and apply retention policy
            Map<Integer, List<BackupResponse>> backupsByVm = filteredBackups.stream()
                    .collect(Collectors.groupingBy(BackupResponse::vmId));

            List<BackupDeletionCandidate> candidates = new ArrayList<>();

            for (Map.Entry<Integer, List<BackupResponse>> entry : backupsByVm.entrySet()) {
                int vmId = entry.getKey();
                List<BackupResponse> vmBackups = entry.getValue();
                VMResponse vm = vmMap.get(vmId);
                String vmName = vm != null ? vm.name() : "Unknown VM " + vmId;

                // Sort backups by creation date (newest first)
                vmBackups.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

                // Apply retention policy
                List<BackupResponse> toDelete = applyRetentionPolicy(vmBackups, policy);

                // Convert to deletion candidates
                for (BackupResponse backup : toDelete) {
                    if (!includeProtected && backup.isProtected()) {
                        continue;
                    }

                    candidates.add(new BackupDeletionCandidate(
                            backup.volid(),
                            backup.vmId(),
                            vmName,
                            backup.size(),
                            backup.sizeHuman(),
                            backup.createdAt(),
                            policy.getReason(),
                            backup.isProtected()
                    ));
                }
            }

            // Sort by creation date (oldest first)
            candidates.sort(Comparator.comparing(BackupDeletionCandidate::createdAt));

            return candidates;

        } catch (Exception e) {
            LOG.error("Failed to get retention candidates: {}", e.getMessage());
            throw ProxmoxException.internalError("get retention candidates", e);
        }
    }

    /**
     * Clean up old backups based on retention policy
     */
    public BackupCleanupResponse cleanupBackups(BackupCleanupRequest request, @AuthTicket String ticket) {
        LOG.info("Starting backup cleanup with policy: {}, dryRun: {}",
                 request.retentionPolicy(), request.dryRun());

        try {
            // Get deletion candidates
            List<BackupDeletionCandidate> candidates = getRetentionCandidates(
                    request.retentionPolicy(),
                    request.tags(),
                    request.vmIds(),
                    request.ignoreProtected(),
                    ticket
            );

            // Calculate statistics
            long totalSizeToFree = candidates.stream()
                    .mapToLong(BackupDeletionCandidate::size)
                    .sum();

            int protectedCount = (int) candidates.stream()
                    .filter(BackupDeletionCandidate::isProtected)
                    .count();

            // If not dry run, delete the backups
            if (!request.dryRun()) {
                for (BackupDeletionCandidate candidate : candidates) {
                    if (candidate.isProtected() && !request.ignoreProtected()) {
                        LOG.info("Skipping protected backup: {}", candidate.volid());
                        continue;
                    }

                    try {
                        LOG.info("Deleting backup: {} ({})", candidate.volid(), candidate.sizeHuman());
                        backupService.deleteBackup(candidate.volid(), ticket);
                    } catch (Exception e) {
                        LOG.error("Failed to delete backup {}: {}", candidate.volid(), e.getMessage());
                    }
                }
            }

            return new BackupCleanupResponse(
                    candidates.size(),
                    candidates.size(),
                    protectedCount,
                    0, // TODO: Calculate retained backups
                    totalSizeToFree,
                    humanReadableSize(totalSizeToFree),
                    candidates,
                    request.dryRun()
            );

        } catch (Exception e) {
            LOG.error("Failed to cleanup backups: {}", e.getMessage());
            throw ProxmoxException.internalError("cleanup backups", e);
        }
    }

    /**
     * Update backup protection status
     */
    public void updateBackupProtection(String volid, boolean protect, @AuthTicket String ticket) {
        LOG.info("Updating backup {} protection to: {}", volid, protect);

        try {
            // Parse volid to get storage and volume
            String[] parts = volid.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid volid format: " + volid);
            }

            String storage = parts[0];
            String volume = parts[1];

            // Find which node has this storage
            // This is a simplified approach - in production, you'd need to check all nodes
            String node = findNodeForBackup(volid, ticket);

            // Update protection using config endpoint
            String csrfToken = ticketManager.getCsrfToken();
            String formData = String.format("protected=%d", protect ? 1 : 0);

            // Note: This is a conceptual endpoint - Proxmox might use a different approach
            // You might need to use the storage content update endpoint
            LOG.warn("Backup protection update not fully implemented - Proxmox API endpoint needed");

        } catch (Exception e) {
            LOG.error("Failed to update backup protection: {}", e.getMessage());
            throw ProxmoxException.internalError("update backup protection", e);
        }
    }

    /**
     * Update backup notes
     */
    public void updateBackupNotes(String volid, String notes, @AuthTicket String ticket) {
        LOG.info("Updating backup {} notes", volid);

        // Similar to protection update - needs proper Proxmox API endpoint
        LOG.warn("Backup notes update not fully implemented - Proxmox API endpoint needed");
    }

    /**
     * Filter backups based on VM tags and IDs
     */
    private List<BackupResponse> filterBackupsByVMCriteria(List<BackupResponse> backups,
                                                           Map<Integer, VMResponse> vmMap,
                                                           List<String> tags,
                                                           List<Integer> vmIds) {
        return backups.stream()
                .filter(backup -> {
                    // Filter by VM IDs if specified
                    if (vmIds != null && !vmIds.isEmpty() && !vmIds.contains(backup.vmId())) {
                        return false;
                    }

                    // Filter by tags if specified
                    if (tags != null && !tags.isEmpty()) {
                        VMResponse vm = vmMap.get(backup.vmId());
                        if (vm == null) {
                            return false;
                        }

                        List<String> vmTags = vm.tags() != null
                                ? vm.tags()
                                : Collections.emptyList();
                        return tags.stream().anyMatch(vmTags::contains);
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Apply retention policy to a list of backups
     */
    private List<BackupResponse> applyRetentionPolicy(List<BackupResponse> backups, RetentionPolicy policy) {
        List<BackupResponse> toDelete = new ArrayList<>();

        switch (policy.type) {
            case DAYS:
                Instant cutoffDate = Instant.now().minus(policy.value, ChronoUnit.DAYS);
                for (BackupResponse backup : backups) {
                    if (backup.createdAt().isBefore(cutoffDate)) {
                        toDelete.add(backup);
                    }
                }
                break;

            case COUNT:
                if (backups.size() > policy.value) {
                    // Keep the newest N backups, delete the rest
                    toDelete.addAll(backups.subList(policy.value, backups.size()));
                }
                break;

            case MONTHLY:
                // Keep the first backup of each month for the last N months
                Map<String, BackupResponse> monthlyBackups = new LinkedHashMap<>();

                for (BackupResponse backup : backups) {
                    String monthKey = backup.createdAt().toString().substring(0, 7); // YYYY-MM
                    monthlyBackups.putIfAbsent(monthKey, backup);
                }

                // Keep only the last N monthly backups
                List<BackupResponse> monthlyList = new ArrayList<>(monthlyBackups.values());
                if (monthlyList.size() > policy.value) {
                    List<BackupResponse> toKeep = monthlyList.subList(0, policy.value);

                    // Mark all backups for deletion except the monthly ones we're keeping
                    for (BackupResponse backup : backups) {
                        if (!toKeep.contains(backup)) {
                            toDelete.add(backup);
                        }
                    }
                }
                break;
        }

        return toDelete;
    }

    /**
     * Parse retention policy string
     */
    private RetentionPolicy parseRetentionPolicy(String policyString) {
        Pattern pattern = Pattern.compile("(days|count|monthly):([0-9]+)");
        Matcher matcher = pattern.matcher(policyString);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid retention policy format: " + policyString);
        }

        String type = matcher.group(1);
        int value = Integer.parseInt(matcher.group(2));

        return new RetentionPolicy(RetentionType.valueOf(type.toUpperCase()), value);
    }

    /**
     * Find which node contains a backup
     */
    private String findNodeForBackup(String volid, String ticket) {
        // This is a simplified approach - you might need to search all nodes
        // For now, assume the backup is on the first node
        List<Node> nodes = nodeService.listNodes(ticket);
        if (!nodes.isEmpty()) {
            return nodes.get(0).getName();
        }
        throw ProxmoxException.notFound("nodes", "cluster",
            "No nodes available in the cluster");
    }

    /**
     * Convert bytes to human-readable format
     */
    private String humanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "iB";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Internal retention policy representation
     */
    private static class RetentionPolicy {
        final RetentionType type;
        final int value;

        RetentionPolicy(RetentionType type, int value) {
            this.type = type;
            this.value = value;
        }

        String getReason() {
            switch (type) {
                case DAYS:
                    return "Older than " + value + " days";
                case COUNT:
                    return "Exceeds retention count of " + value;
                case MONTHLY:
                    return "Not a monthly backup to keep (keeping " + value + " monthly backups)";
                default:
                    return "Unknown retention policy";
            }
        }
    }

    private enum RetentionType {
        DAYS, COUNT, MONTHLY
    }
}
