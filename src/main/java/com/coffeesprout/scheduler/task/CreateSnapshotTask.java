package com.coffeesprout.scheduler.task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.service.SnapshotService;
import io.quarkus.arc.Unremovable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task for creating VM snapshots.
 *
 * Configuration parameters (set in JobParameter):
 * - snapshotNamePattern: Pattern for snapshot names (supports {vm}, {date}, {time}, {datetime})
 * - includeVmState: Whether to include VM state in snapshot (default: false)
 * - description: Description for the snapshot
 * - maxSnapshots: Maximum number of snapshots to keep per VM (optional, for rotation)
 * - snapshotTTL: Time-to-live in hours (optional, adds TTL info to description)
 */
@ApplicationScoped
@Unremovable
public class CreateSnapshotTask extends AbstractVMTask {

    private static final Logger LOG = LoggerFactory.getLogger(CreateSnapshotTask.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Inject
    SnapshotService snapshotService;

    @Override
    public String getTaskType() {
        return "snapshot_create";
    }

    @Override
    protected Map<String, Object> processVM(TaskContext context, VMResponse vm) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // Extract configuration from context parameters
        String namePattern = context.getParameter("snapshotNamePattern", "scheduled-{vm}-{datetime}");
        boolean includeVmState = context.getBooleanParameter("includeVmState", false);
        String description = context.getParameter("description", "Scheduled snapshot");
        Integer maxSnapshots = context.getIntParameter("maxSnapshots", null);
        Integer snapshotTTL = context.getIntParameter("snapshotTTL", null);

        // Generate snapshot name
        String snapshotName = generateSnapshotName(namePattern, vm.name() != null ? vm.name() : "vm-" + vm.vmid());

        // Create snapshot request
        CreateSnapshotRequest request = new CreateSnapshotRequest(
            snapshotName,
            description,
            includeVmState,
            snapshotTTL
        );

        LOG.info("Creating snapshot '{}' for VM {} ({})", snapshotName, vm.vmid(), vm.name());

        // Create snapshot
        var taskResponse = snapshotService.createSnapshot(vm.vmid(), request, null);

        result.put("snapshotName", snapshotName);
        result.put("taskId", taskResponse.taskId());

        String message = String.format("Created snapshot '%s' (task: %s)", snapshotName, taskResponse.taskId());
        if (snapshotTTL != null && snapshotTTL > 0) {
            message += String.format(" - TTL: %d hours", snapshotTTL);
            result.put("ttlHours", snapshotTTL);
        }
        result.put("message", message);

        // Handle snapshot rotation if configured
        if (maxSnapshots != null && maxSnapshots > 0) {
            int deletedCount = rotateSnapshots(vm.vmid(), vm.name(), maxSnapshots);
            if (deletedCount > 0) {
                result.put("rotated", deletedCount);
                result.put("rotationMessage", String.format("Deleted %d old snapshots", deletedCount));
            }
        }

        return result;
    }

    @Override
    public void validateConfiguration(TaskContext context) {
        // Validate snapshot name pattern if provided
        String pattern = context.getParameter("snapshotNamePattern");
        if (pattern != null && !pattern.contains("{vm}") && !pattern.contains("{date}") &&
            !pattern.contains("{time}") && !pattern.contains("{datetime}")) {
            throw new IllegalArgumentException(
                "snapshotNamePattern should contain at least one placeholder: {vm}, {date}, {time}, or {datetime}"
            );
        }

        // Validate maxSnapshots if provided
        Integer maxSnapshots = context.getIntParameter("maxSnapshots", null);
        if (maxSnapshots != null && maxSnapshots < 1) {
            throw new IllegalArgumentException("maxSnapshots must be at least 1");
        }

        // Validate snapshotTTL if provided
        Integer snapshotTTL = context.getIntParameter("snapshotTTL", null);
        if (snapshotTTL != null && snapshotTTL < 1) {
            throw new IllegalArgumentException("snapshotTTL must be at least 1 hour");
        }
    }

    private String generateSnapshotName(String pattern, String vmName) {
        LocalDateTime now = LocalDateTime.now();

        return pattern
            .replace("{vm}", vmName)
            .replace("{date}", now.format(DATE_FORMAT))
            .replace("{time}", now.format(TIME_FORMAT))
            .replace("{datetime}", now.format(DATETIME_FORMAT));
    }

    private int rotateSnapshots(Integer vmId, String vmName, int maxSnapshots) {
        int deletedCount = 0;

        try {
            // Get current snapshots
            List<SnapshotResponse> snapshots = snapshotService.listSnapshots(vmId, null);

            // Filter to only scheduled snapshots (by prefix)
            List<SnapshotResponse> scheduledSnapshots = snapshots.stream()
                .filter(s -> s.name() != null && s.name().startsWith("scheduled-"))
                .sorted((a, b) -> {
                    // Sort by snapshot time, oldest first
                    Long timeA = a.createdAt() != null ? a.createdAt() : 0L;
                    Long timeB = b.createdAt() != null ? b.createdAt() : 0L;
                    return timeA.compareTo(timeB);
                })
                .toList();

            // Delete oldest snapshots if we exceed the limit
            int toDelete = scheduledSnapshots.size() - maxSnapshots + 1; // +1 for the new one we just created

            if (toDelete > 0) {
                for (int i = 0; i < toDelete && i < scheduledSnapshots.size(); i++) {
                    SnapshotResponse snapshot = scheduledSnapshots.get(i);
                    try {
                        LOG.info("Deleting old snapshot '{}' for VM {} (rotation)", snapshot.name(), vmId);
                        snapshotService.deleteSnapshot(vmId, snapshot.name(), null);
                        deletedCount++;
                    } catch (Exception e) {
                        LOG.warn("Failed to delete old snapshot '{}' for VM {}: {}",
                               snapshot.name(), vmId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to rotate snapshots for VM {}: {}", vmId, e.getMessage());
        }

        return deletedCount;
    }
}
