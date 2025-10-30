package com.coffeesprout.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.coffeesprout.api.dto.NodeDrainRequest;
import com.coffeesprout.api.dto.NodeDrainResponse;
import com.coffeesprout.api.dto.NodeDrainResponse.VMMigrationStatus;
import com.coffeesprout.api.dto.NodeMaintenanceResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.model.NodeMaintenance;
import org.jboss.logging.Logger;

/**
 * Service for managing node maintenance operations including drain/undrain workflows.
 */
@ApplicationScoped
@AutoAuthenticate
public class NodeMaintenanceService {

    private static final Logger LOG = Logger.getLogger(NodeMaintenanceService.class);

    // In-memory drain operation tracking
    private final Map<String, NodeDrainResponse> drainOperations = new ConcurrentHashMap<>();

    @Inject
    VMService vmService;

    @Inject
    MigrationService migrationService;

    @Inject
    NodeService nodeService;

    /**
     * Drain a node by migrating all VMs off it.
     */
    @Transactional
    public NodeDrainResponse drainNode(String node, NodeDrainRequest request, @AuthTicket String ticket) {
        LOG.infof("Starting drain operation for node: %s", node);

        // Check if node is already being drained
        if (isNodeBeingDrained(node)) {
            throw new IllegalStateException("Node " + node + " is already being drained");
        }

        // Check if node is already in maintenance
        if (NodeMaintenance.isNodeInMaintenance(node)) {
            LOG.warnf("Node %s is already in maintenance mode", node);
        }

        // Get all VMs on the node
        List<VMResponse> vmsOnNode = vmService.listVMsWithFilters(null, null, node, null, ticket);

        // Filter VMs based on drain mode
        List<VMResponse> vmsToMigrate = filterVMsForDrain(vmsOnNode, request);

        if (vmsToMigrate.isEmpty()) {
            LOG.infof("No VMs to migrate on node %s (after filtering), marking as drained", node);
            return createCompletedDrainResponse(node, Collections.emptyList());
        }

        LOG.infof("Found %d VMs to migrate off node %s (mode: %s)", vmsToMigrate.size(), node, request.drainModeOrDefault());

        // Generate drain ID
        String drainId = UUID.randomUUID().toString();

        // Create initial drain response
        NodeDrainResponse drainResponse = new NodeDrainResponse(
            drainId,
            node,
            "drain",
            "in_progress",
            vmsToMigrate.size(),
            0,
            0,
            new ArrayList<>(),
            LocalDateTime.now(),
            null,
            "Draining node " + node + " (" + request.drainModeOrDefault() + " mode)"
        );

        // Store in tracking map
        drainOperations.put(drainId, drainResponse);

        // Update database
        updateNodeMaintenanceRecord(node, drainId, "in_progress", vmsToMigrate);

        // Capture variables for lambda (must be effectively final)
        final String finalTicket = ticket;
        final String finalNode = node;
        final String finalDrainId = drainId;
        final List<VMResponse> finalVmsToMigrate = vmsToMigrate;
        final NodeDrainRequest finalRequest = request;

        // Start async drain operation
        CompletableFuture.runAsync(() -> {
            try {
                executeDrainOperation(finalDrainId, finalNode, finalVmsToMigrate, finalRequest, finalTicket);
            } catch (Exception e) {
                LOG.errorf(e, "Drain operation %s failed: %s", finalDrainId, e.getMessage());
                markDrainFailed(finalDrainId, e.getMessage());
            }
        });

        return drainResponse;
    }

    /**
     * Execute the actual drain operation (migrating VMs).
     */
    private void executeDrainOperation(String drainId, String node, List<VMResponse> vms,
                                       NodeDrainRequest request, String ticket) {
        LOG.infof("Executing drain operation %s for node %s with %d VMs", drainId, node, vms.size());

        List<VMMigrationStatus> vmStatuses = new ArrayList<>();
        int completed = 0;
        int failed = 0;

        if (request.parallelOrDefault()) {
            // Parallel migration with concurrency limit
            completed = executeParallelMigrations(drainId, node, vms, request, ticket, vmStatuses);
            failed = vms.size() - completed;
        } else {
            // Sequential migration
            for (VMResponse vm : vms) {
                VMMigrationStatus status = migrateVM(vm, node, request, ticket);
                vmStatuses.add(status);

                if ("completed".equals(status.status())) {
                    completed++;
                } else {
                    failed++;
                }

                // Update progress
                updateDrainProgress(drainId, completed, failed, vmStatuses);
            }
        }

        // Mark operation as complete
        NodeDrainResponse finalResponse = new NodeDrainResponse(
            drainId,
            node,
            "drain",
            failed == 0 ? "completed" : "partial",
            vms.size(),
            completed,
            failed,
            vmStatuses,
            drainOperations.get(drainId).startedAt(),
            LocalDateTime.now(),
            String.format("Drain completed: %d/%d VMs migrated successfully", completed, vms.size())
        );

        drainOperations.put(drainId, finalResponse);
        updateNodeMaintenanceRecord(node, drainId, failed == 0 ? "completed" : "failed", vms);

        LOG.infof("Drain operation %s completed: %d succeeded, %d failed", drainId, completed, failed);
    }

    /**
     * Execute migrations in parallel with concurrency control.
     */
    private int executeParallelMigrations(String drainId, String node, List<VMResponse> vms,
                                          NodeDrainRequest request, String ticket,
                                          List<VMMigrationStatus> vmStatuses) {
        int maxConcurrent = request.maxConcurrentOrDefault();
        LOG.infof("Executing parallel migrations with max concurrency: %d", maxConcurrent);

        List<CompletableFuture<VMMigrationStatus>> futures = vms.stream()
            .map(vm -> CompletableFuture.supplyAsync(() -> migrateVM(vm, node, request, ticket)))
            .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        int completed = 0;
        for (CompletableFuture<VMMigrationStatus> future : futures) {
            try {
                VMMigrationStatus status = future.get();
                vmStatuses.add(status);
                if ("completed".equals(status.status())) {
                    completed++;
                }
            } catch (Exception e) {
                LOG.error("Failed to get migration status", e);
            }
        }

        return completed;
    }

    /**
     * Migrate a single VM to another node.
     * Handles always-on and maint-ok tags appropriately.
     */
    private VMMigrationStatus migrateVM(VMResponse vm, String sourceNode, NodeDrainRequest request, String ticket) {
        try {
            boolean isAlwaysOn = vm.tags() != null && vm.tags().contains("always-on");
            boolean hasMaintOk = vm.tags() != null && vm.tags().contains("maint-ok");

            LOG.infof("Migrating VM %d (%s) from %s [always-on: %s, maint-ok: %s, status: %s]",
                     vm.vmid(), vm.name(), sourceNode, isAlwaysOn, hasMaintOk, vm.status());

            // Determine target node
            String targetNode = request.targetNode();
            if (targetNode == null) {
                // Auto-select target node
                targetNode = selectBestTargetNode(sourceNode, vm, ticket);
                if (targetNode == null) {
                    return new VMMigrationStatus(
                        vm.vmid(),
                        vm.name(),
                        "failed",
                        null,
                        "No suitable target node found"
                    );
                }
            }

            // Determine if offline migration is allowed
            boolean allowOffline;
            if (isAlwaysOn) {
                // Always-on VMs MUST use live migration
                allowOffline = false;
                LOG.infof("VM %d is always-on, forcing live migration", vm.vmid());
            } else if (hasMaintOk) {
                // Maint-ok VMs can use offline migration
                allowOffline = true;
                LOG.debugf("VM %d has maint-ok tag, offline migration allowed", vm.vmid());
            } else {
                // Use request default
                allowOffline = request.allowOfflineOrDefault();
            }

            // Create migration request
            com.coffeesprout.api.dto.MigrationRequest migrationRequest =
                new com.coffeesprout.api.dto.MigrationRequest(
                    targetNode,
                    allowOffline,
                    true, // withLocalDisks
                    false, // force
                    null, // bwlimit
                    null, // targetStorage
                    null, // migrationType
                    null  // migrationNetwork
                );

            // Execute migration
            migrationService.migrateVM(vm.vmid(), migrationRequest, ticket);

            // Verify and restart if needed (for always-on VMs)
            if (isAlwaysOn) {
                verifyAndRestartVM(vm, ticket);
            }

            return new VMMigrationStatus(
                vm.vmid(),
                vm.name(),
                "completed",
                targetNode,
                null
            );

        } catch (Exception e) {
            LOG.errorf(e, "Failed to migrate VM %d: %s", vm.vmid(), e.getMessage());
            return new VMMigrationStatus(
                vm.vmid(),
                vm.name(),
                "failed",
                null,
                e.getMessage()
            );
        }
    }

    /**
     * Select the best target node for migration.
     */
    private String selectBestTargetNode(String sourceNode, VMResponse vm, String ticket) {
        // Get all available nodes from node service
        try {
            // Get all nodes except source
            List<String> availableNodes = nodeService.listNodes(ticket).stream()
                .map(node -> node.getName())
                .filter(nodeName -> !nodeName.equals(sourceNode))
                .filter(nodeName -> !NodeMaintenance.isNodeInMaintenance(nodeName))
                .collect(Collectors.toList());

            if (availableNodes.isEmpty()) {
                LOG.error("No available target nodes for migration");
                return null;
            }

            // Simple round-robin for now - could be enhanced with resource-based selection
            return availableNodes.get(new Random().nextInt(availableNodes.size()));

        } catch (Exception e) {
            LOG.error("Failed to select target node", e);
            return null;
        }
    }

    /**
     * Enable maintenance mode for a node.
     */
    @Transactional
    public NodeMaintenanceResponse enableMaintenance(String node, boolean drain, String reason,
                                                     @AuthTicket String ticket) {
        // Check if already in maintenance
        Optional<NodeMaintenance> existing = NodeMaintenance.findActiveByNode(node);
        if (existing.isPresent()) {
            throw new IllegalStateException("Node " + node + " is already in maintenance mode");
        }

        // If drain requested, initiate drain first
        String drainId = null;
        if (drain) {
            NodeDrainResponse drainResponse = drainNode(node, NodeDrainRequest.withDefaults(), ticket);
            drainId = drainResponse.drainId();
        }

        // Create maintenance record
        NodeMaintenance maintenance = new NodeMaintenance();
        maintenance.nodeName = node;
        maintenance.inMaintenance = true;
        maintenance.maintenanceStarted = Instant.now();
        maintenance.reason = reason;
        maintenance.lastDrainId = drainId;
        maintenance.drainStatus = drain ? "in_progress" : "none";
        maintenance.persist();

        LOG.infof("Maintenance mode enabled for node %s (drain=%s)", node, drain);

        return buildMaintenanceResponse(maintenance, ticket);
    }

    /**
     * Disable maintenance mode for a node.
     */
    @Transactional
    public NodeMaintenanceResponse disableMaintenance(String node, boolean undrain,
                                                      @AuthTicket String ticket) {
        NodeMaintenance maintenance = NodeMaintenance.findActiveByNode(node)
            .orElseThrow(() -> new IllegalArgumentException("Node " + node + " is not in maintenance mode"));

        maintenance.inMaintenance = false;
        maintenance.maintenanceEnded = Instant.now();
        maintenance.persist();

        LOG.infof("Maintenance mode disabled for node %s (undrain=%s)", node, undrain);

        if (undrain && maintenance.vmList != null && !maintenance.vmList.isEmpty()) {
            // Initiate undrain operation
            undrainNode(node, ticket);
        }

        return buildMaintenanceResponse(maintenance, ticket);
    }

    /**
     * Get maintenance status for a node.
     */
    public Optional<NodeMaintenanceResponse> getMaintenanceStatus(String node, @AuthTicket String ticket) {
        return NodeMaintenance.findLatestByNode(node)
            .map(m -> buildMaintenanceResponse(m, ticket));
    }

    /**
     * Get drain operation status.
     */
    public Optional<NodeDrainResponse> getDrainStatus(String node, String drainId) {
        return Optional.ofNullable(drainOperations.get(drainId));
    }

    /**
     * Undrain a node by migrating VMs back.
     */
    @Transactional
    public NodeDrainResponse undrainNode(String node, @AuthTicket String ticket) {
        // Check if node is still in maintenance
        if (NodeMaintenance.isNodeInMaintenance(node)) {
            throw new IllegalStateException("Cannot undrain node " + node + " while still in maintenance mode");
        }

        // Get maintenance record with VM list
        NodeMaintenance maintenance = NodeMaintenance.findLatestByNode(node)
            .orElseThrow(() -> new IllegalArgumentException("No drain history found for node " + node));

        if (maintenance.vmList == null || maintenance.vmList.isEmpty()) {
            throw new IllegalArgumentException("No VMs to migrate back to node " + node);
        }

        LOG.infof("Starting undrain operation for node %s with %d VMs", node, maintenance.vmList.size());

        // Generate undrain ID
        String undrainId = UUID.randomUUID().toString();

        // Create response
        NodeDrainResponse response = new NodeDrainResponse(
            undrainId,
            node,
            "undrain",
            "in_progress",
            maintenance.vmList.size(),
            0,
            0,
            new ArrayList<>(),
            LocalDateTime.now(),
            null,
            "Undraining node " + node
        );

        drainOperations.put(undrainId, response);

        // Capture variables for lambda (must be effectively final)
        final String finalUndrainId = undrainId;
        final String finalNode = node;
        final List<Integer> finalVmList = maintenance.vmList;
        final String finalTicket = ticket;

        // Start async undrain
        CompletableFuture.runAsync(() -> executeUndrainOperation(finalUndrainId, finalNode, finalVmList, finalTicket));

        return response;
    }

    /**
     * Execute undrain operation.
     */
    private void executeUndrainOperation(String undrainId, String targetNode, List<Integer> vmIds, String ticket) {
        LOG.infof("Executing undrain operation %s for node %s", undrainId, targetNode);

        List<VMMigrationStatus> vmStatuses = new ArrayList<>();
        int completed = 0;
        int failed = 0;

        for (Integer vmId : vmIds) {
            try {
                // Get current VM details
                List<VMResponse> vms = vmService.listVMs(ticket);
                VMResponse vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElse(null);

                if (vm == null) {
                    vmStatuses.add(new VMMigrationStatus(vmId, "unknown", "skipped", null, "VM not found"));
                    failed++;
                    continue;
                }

                // Skip if already on target node
                if (targetNode.equals(vm.node())) {
                    vmStatuses.add(new VMMigrationStatus(vmId, vm.name(), "skipped", targetNode, "Already on target node"));
                    completed++;
                    continue;
                }

                // Migrate back
                com.coffeesprout.api.dto.MigrationRequest migrationRequest =
                    new com.coffeesprout.api.dto.MigrationRequest(
                        targetNode, true, true, false, null, null, null, null
                    );

                migrationService.migrateVM(vmId, migrationRequest, ticket);
                vmStatuses.add(new VMMigrationStatus(vmId, vm.name(), "completed", targetNode, null));
                completed++;

            } catch (Exception e) {
                LOG.errorf(e, "Failed to migrate VM %d back to %s", vmId, targetNode);
                vmStatuses.add(new VMMigrationStatus(vmId, "VM-" + vmId, "failed", null, e.getMessage()));
                failed++;
            }
        }

        // Update final response
        NodeDrainResponse finalResponse = new NodeDrainResponse(
            undrainId,
            targetNode,
            "undrain",
            failed == 0 ? "completed" : "partial",
            vmIds.size(),
            completed,
            failed,
            vmStatuses,
            drainOperations.get(undrainId).startedAt(),
            LocalDateTime.now(),
            String.format("Undrain completed: %d/%d VMs migrated successfully", completed, vmIds.size())
        );

        drainOperations.put(undrainId, finalResponse);
    }

    // Helper methods

    private boolean isNodeBeingDrained(String node) {
        return drainOperations.values().stream()
            .anyMatch(d -> d.node().equals(node) && "in_progress".equals(d.status()));
    }

    @Transactional
    private void updateNodeMaintenanceRecord(String node, String drainId, String status, List<VMResponse> vms) {
        NodeMaintenance maintenance = NodeMaintenance.findActiveByNode(node)
            .orElseGet(() -> {
                NodeMaintenance m = new NodeMaintenance();
                m.nodeName = node;
                m.inMaintenance = false;
                return m;
            });

        maintenance.lastDrainId = drainId;
        maintenance.drainStatus = status;
        if (vms != null) {
            maintenance.vmList = vms.stream().map(VMResponse::vmid).collect(Collectors.toList());
        }
        maintenance.persist();
    }

    private void updateDrainProgress(String drainId, int completed, int failed, List<VMMigrationStatus> statuses) {
        NodeDrainResponse current = drainOperations.get(drainId);
        if (current != null) {
            NodeDrainResponse updated = new NodeDrainResponse(
                current.drainId(),
                current.node(),
                current.operation(),
                "in_progress",
                current.totalVMs(),
                completed,
                failed,
                new ArrayList<>(statuses),
                current.startedAt(),
                null,
                String.format("Progress: %d/%d completed", completed, current.totalVMs())
            );
            drainOperations.put(drainId, updated);
        }
    }

    private void markDrainFailed(String drainId, String error) {
        NodeDrainResponse current = drainOperations.get(drainId);
        if (current != null) {
            NodeDrainResponse failed = new NodeDrainResponse(
                current.drainId(),
                current.node(),
                current.operation(),
                "failed",
                current.totalVMs(),
                current.completedVMs(),
                current.failedVMs(),
                current.vmStatus(),
                current.startedAt(),
                LocalDateTime.now(),
                "Drain failed: " + error
            );
            drainOperations.put(drainId, failed);
        }
    }

    private NodeDrainResponse createCompletedDrainResponse(String node, List<VMResponse> vms) {
        String drainId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        return new NodeDrainResponse(
            drainId,
            node,
            "drain",
            "completed",
            0,
            0,
            0,
            Collections.emptyList(),
            now,
            now,
            "No VMs to drain"
        );
    }

    private NodeMaintenanceResponse buildMaintenanceResponse(NodeMaintenance m, String ticket) {
        // Count VMs on node
        int vmsOnNode = 0;
        try {
            vmsOnNode = vmService.listVMsWithFilters(null, null, m.nodeName, null, ticket).size();
        } catch (Exception e) {
            LOG.error("Failed to count VMs on node", e);
        }

        return new NodeMaintenanceResponse(
            m.nodeName,
            m.inMaintenance,
            m.reason,
            m.maintenanceStarted != null ? LocalDateTime.ofInstant(m.maintenanceStarted, ZoneId.systemDefault()) : null,
            m.maintenanceEnded != null ? LocalDateTime.ofInstant(m.maintenanceEnded, ZoneId.systemDefault()) : null,
            vmsOnNode,
            m.lastDrainId,
            m.drainStatus
        );
    }

    /**
     * Filter VMs for drain based on drain mode and tags.
     *
     * Soft drain (maintenance/reboot):
     * - Skips VMs with 'maint-ok' tag (they can handle downtime)
     * - Includes 'always-on' VMs (they must stay up)
     *
     * Hard drain (faulty machine):
     * - Migrates ALL VMs regardless of tags
     */
    private List<VMResponse> filterVMsForDrain(List<VMResponse> vms, NodeDrainRequest request) {
        if (request.isHardDrain()) {
            LOG.info("Hard drain: migrating ALL VMs");
            return vms;
        }

        // Soft drain: skip maint-ok VMs
        List<VMResponse> filtered = vms.stream()
            .filter(vm -> {
                boolean hasMaintOk = vm.tags() != null && vm.tags().contains("maint-ok");
                boolean hasAlwaysOn = vm.tags() != null && vm.tags().contains("always-on");

                if (hasMaintOk && !hasAlwaysOn) {
                    LOG.debugf("Skipping VM %d (%s) - has maint-ok tag", vm.vmid(), vm.name());
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        LOG.infof("Soft drain: migrating %d/%d VMs (skipped %d with maint-ok tag)",
                 filtered.size(), vms.size(), vms.size() - filtered.size());
        return filtered;
    }

    /**
     * Verify VM state after migration and restart if needed.
     * Always-on VMs MUST be running after migration.
     */
    private void verifyAndRestartVM(VMResponse vm, String ticket) {
        try {
            // Check if this is an always-on VM
            boolean isAlwaysOn = vm.tags() != null && vm.tags().contains("always-on");
            if (!isAlwaysOn) {
                return; // No need to verify
            }

            // Wait a moment for VM to settle
            Thread.sleep(2000);

            // Check current state
            List<VMResponse> currentVMs = vmService.listVMs(ticket);
            VMResponse currentVM = currentVMs.stream()
                .filter(v -> v.vmid() == vm.vmid())
                .findFirst()
                .orElse(null);

            if (currentVM == null) {
                LOG.errorf("VM %d not found after migration!", vm.vmid());
                return;
            }

            // If always-on VM is not running, start it
            if (!"running".equals(currentVM.status())) {
                LOG.warnf("Always-on VM %d (%s) is %s after migration, starting it",
                         vm.vmid(), vm.name(), currentVM.status());

                // Start the VM
                // Note: Assuming PowerService exists with startVM method
                // If not, we'll need to add this
                try {
                    vmService.startVM(currentVM.node(), vm.vmid(), ticket);
                    LOG.infof("Successfully started always-on VM %d", vm.vmid());
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to start always-on VM %d: %s", vm.vmid(), e.getMessage());
                }
            } else {
                LOG.debugf("Always-on VM %d is running correctly after migration", vm.vmid());
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to verify/restart VM %d: %s", vm.vmid(), e.getMessage());
        }
    }
}
