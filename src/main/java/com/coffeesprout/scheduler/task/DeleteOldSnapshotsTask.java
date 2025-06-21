package com.coffeesprout.scheduler.task;

import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.service.SnapshotService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scheduled task for deleting old VM snapshots based on various criteria.
 * 
 * Configuration parameters (set in JobParameter):
 * - ageThresholdHours: Delete snapshots older than X hours (required if no namePattern)
 * - namePattern: Only delete snapshots matching this pattern (supports wildcards)
 * - checkDescription: Parse TTL from snapshot descriptions (default: false)
 * - safeMode: Only delete snapshots created by Moxxie (default: true)
 * - dryRun: Log what would be deleted without actually deleting (default: false)
 */
@ApplicationScoped
@Unremovable
public class DeleteOldSnapshotsTask extends AbstractVMTask {
    
    private static final Logger log = LoggerFactory.getLogger(DeleteOldSnapshotsTask.class);
    private static final Pattern TTL_PATTERN = Pattern.compile("TTL:\\s*(\\d+)h");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("(\\d{8}-\\d{6})");
    
    @Inject
    SnapshotService snapshotService;
    
    @Override
    public String getTaskType() {
        return "snapshot_delete";
    }
    
    @Override
    protected Map<String, Object> processVM(TaskContext context, VMResponse vm) throws Exception {
        Map<String, Object> result = new HashMap<>();
        List<String> deletedSnapshots = new ArrayList<>();
        List<String> skippedSnapshots = new ArrayList<>();
        int errorCount = 0;
        
        // Extract configuration
        Integer ageThresholdHours = context.getIntParameter("ageThresholdHours", null);
        String namePattern = context.getParameter("namePattern");
        boolean checkDescription = context.getBooleanParameter("checkDescription", false);
        boolean safeMode = context.getBooleanParameter("safeMode", true);
        boolean dryRun = context.getBooleanParameter("dryRun", false);
        
        // Calculate age threshold
        Instant ageThreshold = null;
        if (ageThresholdHours != null) {
            ageThreshold = Instant.now().minus(ageThresholdHours, ChronoUnit.HOURS);
        }
        
        // Get snapshots for this VM
        List<SnapshotResponse> snapshots = snapshotService.listSnapshots(vm.vmid(), null);
        log.info("Found {} snapshots for VM {} ({})", snapshots.size(), vm.vmid(), vm.name());
        
        for (SnapshotResponse snapshot : snapshots) {
            try {
                boolean shouldDelete = false;
                String reason = "";
                
                // Check name pattern
                if (namePattern != null && !matchesPattern(snapshot.name(), namePattern)) {
                    log.debug("Snapshot '{}' does not match pattern '{}'", snapshot.name(), namePattern);
                    continue;
                }
                
                // Check safe mode (only delete Moxxie-created snapshots)
                if (safeMode && !isMoxxieSnapshot(snapshot)) {
                    skippedSnapshots.add(snapshot.name() + " (not Moxxie-created)");
                    continue;
                }
                
                // Check age threshold
                if (ageThreshold != null) {
                    Long snapshotTime = snapshot.createdAt();
                    if (snapshotTime != null) {
                        Instant snapshotInstant = Instant.ofEpochSecond(snapshotTime);
                        if (snapshotInstant.isBefore(ageThreshold)) {
                            shouldDelete = true;
                            reason = "older than " + ageThresholdHours + " hours";
                        }
                    }
                }
                
                // Check TTL in description
                if (checkDescription && snapshot.description() != null) {
                    Integer ttlHours = extractTTLFromDescription(snapshot.description());
                    if (ttlHours != null) {
                        Long snapshotTime = snapshot.createdAt();
                        if (snapshotTime != null) {
                            Instant expiryTime = Instant.ofEpochSecond(snapshotTime).plus(ttlHours, ChronoUnit.HOURS);
                            if (Instant.now().isAfter(expiryTime)) {
                                shouldDelete = true;
                                reason = "TTL expired (" + ttlHours + "h)";
                            }
                        }
                    }
                }
                
                // Delete if criteria met
                if (shouldDelete) {
                    if (dryRun) {
                        log.info("[DRY RUN] Would delete snapshot '{}' for VM {} - {}", 
                               snapshot.name(), vm.vmid(), reason);
                        deletedSnapshots.add(snapshot.name() + " (dry run: " + reason + ")");
                    } else {
                        log.info("Deleting snapshot '{}' for VM {} - {}", 
                               snapshot.name(), vm.vmid(), reason);
                        snapshotService.deleteSnapshot(vm.vmid(), snapshot.name(), null);
                        deletedSnapshots.add(snapshot.name() + " (" + reason + ")");
                    }
                } else {
                    log.debug("Keeping snapshot '{}' for VM {}", snapshot.name(), vm.vmid());
                }
                
            } catch (Exception e) {
                log.error("Failed to process snapshot '{}' for VM {}: {}", 
                        snapshot.name(), vm.vmid(), e.getMessage());
                errorCount++;
            }
        }
        
        // Build result
        result.put("vmId", vm.vmid());
        result.put("vmName", vm.name());
        result.put("totalSnapshots", snapshots.size());
        result.put("deletedCount", deletedSnapshots.size());
        result.put("deletedSnapshots", deletedSnapshots);
        result.put("skippedCount", skippedSnapshots.size());
        result.put("skippedSnapshots", skippedSnapshots);
        result.put("errorCount", errorCount);
        
        if (dryRun) {
            result.put("message", String.format("[DRY RUN] Would delete %d snapshots", deletedSnapshots.size()));
        } else {
            result.put("message", String.format("Deleted %d snapshots", deletedSnapshots.size()));
        }
        
        return result;
    }
    
    @Override
    public void validateConfiguration(TaskContext context) {
        // At least one deletion criteria must be specified
        Integer ageThresholdHours = context.getIntParameter("ageThresholdHours", null);
        String namePattern = context.getParameter("namePattern");
        boolean checkDescription = context.getBooleanParameter("checkDescription", false);
        
        if (ageThresholdHours == null && namePattern == null && !checkDescription) {
            throw new IllegalArgumentException(
                "At least one deletion criteria must be specified: ageThresholdHours, namePattern, or checkDescription"
            );
        }
        
        // Validate age threshold
        if (ageThresholdHours != null && ageThresholdHours < 1) {
            throw new IllegalArgumentException("ageThresholdHours must be at least 1");
        }
    }
    
    private boolean matchesPattern(String snapshotName, String pattern) {
        if (snapshotName == null || pattern == null) {
            return false;
        }
        
        // Convert wildcard pattern to regex
        String regex = pattern.replace("*", ".*").replace("?", ".");
        return snapshotName.matches(regex);
    }
    
    private boolean isMoxxieSnapshot(SnapshotResponse snapshot) {
        // Check if snapshot was created by Moxxie based on naming patterns
        if (snapshot.name() == null) {
            return false;
        }
        
        // Common Moxxie snapshot patterns
        return snapshot.name().startsWith("scheduled-") ||
               snapshot.name().startsWith("auto-") ||
               snapshot.name().startsWith("pre-update-") ||
               (snapshot.description() != null && 
                (snapshot.description().contains("Moxxie") || 
                 snapshot.description().contains("Scheduled snapshot") ||
                 snapshot.description().contains("TTL:")));
    }
    
    private Integer extractTTLFromDescription(String description) {
        if (description == null) {
            return null;
        }
        
        Matcher matcher = TTL_PATTERN.matcher(description);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse TTL from description: {}", description);
            }
        }
        
        return null;
    }
}