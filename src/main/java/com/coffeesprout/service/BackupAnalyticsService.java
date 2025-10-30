package com.coffeesprout.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.api.exception.ProxmoxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class BackupAnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupAnalyticsService.class);

    @Inject
    BackupService backupService;

    @Inject
    VMService vmService;

    @Inject
    TagService tagService;

    /**
     * Get storage usage per VM
     */
    public List<VMStorageUsage> getVMStorageUsage(@AuthTicket String ticket) {
        LOG.debug("Calculating storage usage per VM");

        try {
            // Get all backups
            List<BackupResponse> allBackups = backupService.listAllBackups(ticket);

            // Get all VMs to get their names and tags
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Map<Integer, VMResponse> vmMap = allVMs.stream()
                    .collect(Collectors.toMap(VMResponse::vmid, vm -> vm));

            // Group backups by VM ID
            Map<Integer, List<BackupResponse>> backupsByVm = allBackups.stream()
                    .collect(Collectors.groupingBy(BackupResponse::vmId));

            // Calculate usage for each VM
            List<VMStorageUsage> usageList = new ArrayList<>();

            for (Map.Entry<Integer, List<BackupResponse>> entry : backupsByVm.entrySet()) {
                int vmId = entry.getKey();
                List<BackupResponse> vmBackups = entry.getValue();

                // Get VM info
                VMResponse vm = vmMap.get(vmId);
                String vmName = vm != null ? vm.name() : "Unknown VM " + vmId;
                List<String> tags = vm != null && vm.tags() != null
                        ? vm.tags()
                        : Collections.emptyList();

                // Calculate totals
                long totalSize = vmBackups.stream()
                        .mapToLong(BackupResponse::size)
                        .sum();

                // Group by storage
                Map<String, Long> sizeByStorage = vmBackups.stream()
                        .collect(Collectors.groupingBy(
                                BackupResponse::storage,
                                Collectors.summingLong(BackupResponse::size)
                        ));

                // Find oldest and newest
                Optional<Instant> oldest = vmBackups.stream()
                        .map(BackupResponse::createdAt)
                        .min(Instant::compareTo);

                Optional<Instant> newest = vmBackups.stream()
                        .map(BackupResponse::createdAt)
                        .max(Instant::compareTo);

                VMStorageUsage usage = new VMStorageUsage(
                        vmId,
                        vmName,
                        totalSize,
                        humanReadableSize(totalSize),
                        vmBackups.size(),
                        oldest.orElse(null),
                        newest.orElse(null),
                        sizeByStorage,
                        tags
                );

                usageList.add(usage);
            }

            // Sort by total size descending
            usageList.sort((a, b) -> Long.compare(b.totalSize(), a.totalSize()));

            return usageList;

        } catch (Exception e) {
            LOG.error("Failed to calculate VM storage usage: {}", e.getMessage());
            throw ProxmoxException.internalError("calculate VM storage usage", e);
        }
    }

    /**
     * Get storage usage per client (based on tags)
     */
    public List<ClientStorageUsage> getClientStorageUsage(@AuthTicket String ticket) {
        LOG.debug("Calculating storage usage per client");

        try {
            // Get VM storage usage first
            List<VMStorageUsage> vmUsages = getVMStorageUsage(ticket);

            // Group by client tag
            Map<String, List<VMStorageUsage>> usageByClient = new HashMap<>();

            for (VMStorageUsage vmUsage : vmUsages) {
                // Find client tag (tags starting with "client-")
                String clientTag = vmUsage.tags().stream()
                        .filter(tag -> tag.startsWith("client-"))
                        .findFirst()
                        .orElse("no-client");

                usageByClient.computeIfAbsent(clientTag, k -> new ArrayList<>()).add(vmUsage);
            }

            // Calculate totals per client
            List<ClientStorageUsage> clientUsages = new ArrayList<>();

            for (Map.Entry<String, List<VMStorageUsage>> entry : usageByClient.entrySet()) {
                String clientTag = entry.getKey();
                List<VMStorageUsage> clientVMs = entry.getValue();

                // Calculate totals
                long totalSize = clientVMs.stream()
                        .mapToLong(VMStorageUsage::totalSize)
                        .sum();

                int totalBackups = clientVMs.stream()
                        .mapToInt(VMStorageUsage::backupCount)
                        .sum();

                // Size by VM
                Map<Integer, Long> sizeByVm = clientVMs.stream()
                        .collect(Collectors.toMap(
                                VMStorageUsage::vmId,
                                VMStorageUsage::totalSize
                        ));

                // Size by storage (aggregate across all VMs)
                Map<String, Long> sizeByStorage = new HashMap<>();
                for (VMStorageUsage vmUsage : clientVMs) {
                    for (Map.Entry<String, Long> storageEntry : vmUsage.sizeByStorage().entrySet()) {
                        sizeByStorage.merge(storageEntry.getKey(), storageEntry.getValue(), Long::sum);
                    }
                }

                ClientStorageUsage usage = new ClientStorageUsage(
                        clientTag,
                        totalSize,
                        humanReadableSize(totalSize),
                        clientVMs.size(),
                        totalBackups,
                        sizeByVm,
                        sizeByStorage
                );

                clientUsages.add(usage);
            }

            // Sort by total size descending
            clientUsages.sort((a, b) -> Long.compare(b.totalSize(), a.totalSize()));

            return clientUsages;

        } catch (Exception e) {
            LOG.error("Failed to calculate client storage usage: {}", e.getMessage());
            throw ProxmoxException.internalError("calculate client storage usage", e);
        }
    }

    /**
     * Get storage usage per storage location
     */
    public List<StorageLocationUsage> getStorageLocationUsage(@AuthTicket String ticket) {
        LOG.debug("Calculating storage usage per location");

        try {
            // Get all backups
            List<BackupResponse> allBackups = backupService.listAllBackups(ticket);

            // Group by storage
            Map<String, List<BackupResponse>> backupsByStorage = allBackups.stream()
                    .collect(Collectors.groupingBy(BackupResponse::storage));

            List<StorageLocationUsage> storageUsages = new ArrayList<>();

            for (Map.Entry<String, List<BackupResponse>> entry : backupsByStorage.entrySet()) {
                String storage = entry.getKey();
                List<BackupResponse> storageBackups = entry.getValue();

                long totalSize = storageBackups.stream()
                        .mapToLong(BackupResponse::size)
                        .sum();

                // TODO: Get actual storage capacity from Proxmox storage API
                // For now, we'll just show usage without capacity
                StorageLocationUsage usage = new StorageLocationUsage(
                        storage,
                        totalSize,
                        humanReadableSize(totalSize),
                        storageBackups.size(),
                        -1L,  // Available space unknown
                        "Unknown",
                        -1.0  // Usage percent unknown
                );

                storageUsages.add(usage);
            }

            // Sort by total size descending
            storageUsages.sort((a, b) -> Long.compare(b.totalSize(), a.totalSize()));

            return storageUsages;

        } catch (Exception e) {
            LOG.error("Failed to calculate storage location usage: {}", e.getMessage());
            throw ProxmoxException.internalError("calculate storage location usage", e);
        }
    }

    /**
     * Get storage trends over time
     * For now, this is a simplified implementation that shows current state
     * In a production system, this would track historical data
     */
    public StorageTrend getStorageTrends(String period, @AuthTicket String ticket) {
        LOG.debug("Calculating storage trends for period: {}", period);

        try {
            // Get all backups
            List<BackupResponse> allBackups = backupService.listAllBackups(ticket);

            // For now, create synthetic trend data based on backup creation times
            // In production, this would query historical data
            List<TrendPoint> dataPoints = new ArrayList<>();

            // Determine time window based on period
            Instant now = Instant.now();
            Instant startTime;
            int intervalDays;

            switch (period.toLowerCase()) {
                case "daily":
                    startTime = now.minus(30, ChronoUnit.DAYS);
                    intervalDays = 1;
                    break;
                case "weekly":
                    startTime = now.minus(12 * 7, ChronoUnit.DAYS);
                    intervalDays = 7;
                    break;
                case "monthly":
                    startTime = now.minus(365, ChronoUnit.DAYS);
                    intervalDays = 30;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid period: " + period);
            }

            // Group backups by time intervals
            Instant currentInterval = startTime;
            while (currentInterval.isBefore(now)) {
                final Instant intervalStart = currentInterval;
                final Instant intervalEnd = currentInterval.plus(intervalDays, ChronoUnit.DAYS);

                // Find backups created in this interval
                List<BackupResponse> intervalBackups = allBackups.stream()
                        .filter(b -> !b.createdAt().isBefore(intervalStart) && b.createdAt().isBefore(intervalEnd))
                        .collect(Collectors.toList());

                // Calculate cumulative size up to this point
                long cumulativeSize = allBackups.stream()
                        .filter(b -> b.createdAt().isBefore(intervalEnd))
                        .mapToLong(BackupResponse::size)
                        .sum();

                int cumulativeCount = (int) allBackups.stream()
                        .filter(b -> b.createdAt().isBefore(intervalEnd))
                        .count();

                long sizeAdded = intervalBackups.stream()
                        .mapToLong(BackupResponse::size)
                        .sum();

                // TODO: Track deletions properly
                long sizeRemoved = 0L;

                TrendPoint point = new TrendPoint(
                        intervalEnd,
                        cumulativeSize,
                        cumulativeCount,
                        sizeAdded,
                        sizeRemoved
                );

                dataPoints.add(point);
                currentInterval = intervalEnd;
            }

            return new StorageTrend(period, dataPoints);

        } catch (Exception e) {
            LOG.error("Failed to calculate storage trends: {}", e.getMessage());
            throw ProxmoxException.internalError("calculate storage trends", e);
        }
    }

    /**
     * Get overall backup health status
     */
    public BackupHealth getBackupHealth(int coverageThresholdDays, int overdueThresholdDays, @AuthTicket String ticket) {
        LOG.debug("Calculating backup health with coverage threshold: {} days, overdue threshold: {} days",
                 coverageThresholdDays, overdueThresholdDays);

        try {
            // Get all VMs
            List<VMResponse> allVMs = vmService.listVMs(ticket);

            // Get all backups
            List<BackupResponse> allBackups = backupService.listAllBackups(ticket);

            // Group backups by VM
            Map<Integer, List<BackupResponse>> backupsByVm = allBackups.stream()
                    .collect(Collectors.groupingBy(BackupResponse::vmId));

            // Calculate coverage for each VM
            List<BackupCoverage> coverage = new ArrayList<>();
            int vmsWithRecentBackup = 0;
            int vmsWithoutBackup = 0;
            int vmsOverdue = 0;

            Instant now = Instant.now();
            Instant coverageThreshold = now.minus(coverageThresholdDays, ChronoUnit.DAYS);
            Instant overdueThreshold = now.minus(overdueThresholdDays, ChronoUnit.DAYS);

            for (VMResponse vm : allVMs) {
                List<BackupResponse> vmBackups = backupsByVm.getOrDefault(vm.vmid(), Collections.emptyList());

                if (vmBackups.isEmpty()) {
                    vmsWithoutBackup++;
                    coverage.add(new BackupCoverage(
                            vm.vmid(),
                            vm.name(),
                            vm.tags() != null
                                    ? vm.tags()
                                    : Collections.emptyList(),
                            null,
                            "NO_BACKUP",
                            true,
                            "No backups found"
                    ));
                } else {
                    // Find most recent backup
                    BackupResponse latestBackup = vmBackups.stream()
                            .max(Comparator.comparing(BackupResponse::createdAt))
                            .orElse(null);

                    if (latestBackup != null) {
                        boolean hasRecentBackup = latestBackup.createdAt().isAfter(coverageThreshold);
                        boolean isOverdue = latestBackup.createdAt().isBefore(overdueThreshold);

                        if (hasRecentBackup) {
                            vmsWithRecentBackup++;
                        }
                        if (isOverdue) {
                            vmsOverdue++;
                        }

                        String overdueReason = null;
                        if (isOverdue) {
                            long daysSinceBackup = ChronoUnit.DAYS.between(latestBackup.createdAt(), now);
                            overdueReason = String.format("Last backup %d days ago (threshold: %d days)",
                                                        daysSinceBackup, overdueThresholdDays);
                        }

                        coverage.add(new BackupCoverage(
                                vm.vmid(),
                                vm.name(),
                                vm.tags() != null
                                    ? vm.tags()
                                    : Collections.emptyList(),
                                latestBackup.createdAt(),
                                "OK",
                                isOverdue,
                                overdueReason
                        ));
                    }
                }
            }

            // Calculate backup age distribution
            Map<String, Integer> backupsByAge = new LinkedHashMap<>();
            backupsByAge.put("< 1 day", 0);
            backupsByAge.put("1-7 days", 0);
            backupsByAge.put("7-30 days", 0);
            backupsByAge.put("30-90 days", 0);
            backupsByAge.put("> 90 days", 0);

            for (BackupResponse backup : allBackups) {
                long daysSinceBackup = ChronoUnit.DAYS.between(backup.createdAt(), now);

                if (daysSinceBackup < 1) {
                    backupsByAge.merge("< 1 day", 1, Integer::sum);
                } else if (daysSinceBackup <= 7) {
                    backupsByAge.merge("1-7 days", 1, Integer::sum);
                } else if (daysSinceBackup <= 30) {
                    backupsByAge.merge("7-30 days", 1, Integer::sum);
                } else if (daysSinceBackup <= 90) {
                    backupsByAge.merge("30-90 days", 1, Integer::sum);
                } else {
                    backupsByAge.merge("> 90 days", 1, Integer::sum);
                }
            }

            return new BackupHealth(
                    allVMs.size(),
                    vmsWithRecentBackup,
                    vmsWithoutBackup,
                    vmsOverdue,
                    coverage,
                    backupsByAge
            );

        } catch (Exception e) {
            LOG.error("Failed to calculate backup health: {}", e.getMessage());
            throw ProxmoxException.internalError("calculate backup health", e);
        }
    }

    /**
     * Get VMs without recent backups
     */
    public List<BackupCoverage> getVMsWithoutRecentBackups(int thresholdDays, @AuthTicket String ticket) {
        BackupHealth health = getBackupHealth(thresholdDays, thresholdDays, ticket);

        // Filter to only show VMs that are overdue or have no backups
        return health.coverage().stream()
                .filter(coverage -> coverage.isOverdue() || coverage.lastBackup() == null)
                .sorted(Comparator.comparing(BackupCoverage::vmName))
                .collect(Collectors.toList());
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
}
