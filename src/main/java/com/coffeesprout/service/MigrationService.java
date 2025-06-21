package com.coffeesprout.service;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.client.MigrationPreconditionsResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.TaskStatusResponse;
import com.coffeesprout.model.VmMigration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class MigrationService {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    VMService vmService;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    TaskService taskService;
    
    /**
     * Migrate a VM to a target node
     */
    @Transactional
    public MigrationResponse migrateVM(int vmId, MigrationRequest request, @AuthTicket String ticket) {
        log.info("Starting migration of VM {} to node {}", vmId, request.targetNode());
        
        // 1. Get VM details to find current node and state
        VMResponse vm;
        try {
            List<VMResponse> vms = vmService.listVMs(ticket);
            vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
        } catch (Exception e) {
            log.error("Failed to get VM details for migration: {}", e.getMessage());
            throw new RuntimeException("Failed to get VM details: " + e.getMessage(), e);
        }
        
        String currentNode = vm.node();
        boolean wasRunning = "running".equals(vm.status());
        
        // Validate target node is different
        if (currentNode.equals(request.targetNode())) {
            throw new RuntimeException("VM is already on node " + currentNode);
        }
        
        // 2. Create migration history record
        VmMigration migration = createMigrationRecord(vm, request);
        
        // 3. Attempt migration directly
        String csrfToken = ticketManager.getCsrfToken();
        
        try {
            // Default to online migration if VM is running
            Integer online = wasRunning ? 1 : null;
            
            log.info("Attempting {} migration for VM {} from {} to {}", 
                wasRunning ? "online" : "offline", vmId, currentNode, request.targetNode());
            
            // Log parameters for debugging
            log.info("Migration parameters - online: {}, withLocalDisks: {}, force: {}, bwlimit: {}, targetStorage: {}, migrationType: {}, migrationNetwork: {}",
                online, request.withLocalDisks() ? 1 : null, request.force() ? 1 : null, 
                request.bwlimit(), request.targetStorage(), request.migrationType(), request.migrationNetwork());
            
            // Execute migration with proper parameters
            // When migrating with local disks, use insecure migration type
            String migrationType = request.migrationType();
            if (request.withLocalDisks() && "secure".equals(migrationType)) {
                migrationType = "insecure";
                log.info("Using insecure migration type for local disk migration");
            }
            
            // TEMPORARY: Don't send migration_type to debug parameter verification issue
            log.info("DEBUG: Calling migrateVM without migration_type parameter");
            
            TaskStatusResponse task = proxmoxClient.migrateVM(
                currentNode,
                vmId,
                request.targetNode(),
                online,  // Send 1 for online if VM is running
                request.withLocalDisks() ? 1 : null,
                request.force() ? 1 : null,
                request.bwlimit(),
                request.targetStorage(),
                null,  // TEMPORARY: Don't send migration_type
                request.migrationNetwork(),
                ticket,
                csrfToken
            );
            
            if (task.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            migration.taskUpid = task.getData();
            migration.persist();
            
            log.info("Migration task {} started for VM {}", task.getData(), vmId);
            
            // 4. Monitor task completion
            try {
                TaskStatusDetailResponse taskStatus = waitForTaskCompletion(task.getData(), ticket);
                
                if (!"OK".equals(taskStatus.exitstatus())) {
                    throw new RuntimeException("Migration task failed: " + taskStatus.exitstatus());
                }
            } catch (Exception e) {
                log.error("Error waiting for migration task: {}", e.getMessage());
                // Continue to check VM state anyway
            }
            
            // 5. Verify VM is on target node and check state
            Thread.sleep(2000); // Give Proxmox a moment to update
            
            List<VMResponse> updatedVms = vmService.listVMs(ticket);
            VMResponse migratedVm = updatedVms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (migratedVm == null) {
                migration.markFailed("VM not found after migration");
                throw new RuntimeException("VM not found after migration");
            }
            
            if (!request.targetNode().equals(migratedVm.node())) {
                migration.markFailed("VM not on target node after migration");
                throw new RuntimeException("VM migration failed - VM is on " + migratedVm.node() + " instead of " + request.targetNode());
            }
            
            // 6. Handle state recovery if needed
            if (wasRunning && !"running".equals(migratedVm.status())) {
                log.info("VM {} was running before migration but stopped after. Attempting to start...", vmId);
                try {
                    vmService.startVM(request.targetNode(), vmId, ticket);
                    // Give it a moment to start
                    Thread.sleep(3000);
                    
                    // Check state again
                    updatedVms = vmService.listVMs(ticket);
                    migratedVm = updatedVms.stream()
                        .filter(v -> v.vmid() == vmId)
                        .findFirst()
                        .orElse(migratedVm);
                    
                } catch (Exception e) {
                    log.error("Failed to restart VM {} after migration: {}", vmId, e.getMessage());
                    migration.postMigrationState = "stopped";
                    migration.errorMessage = "Migration succeeded but VM failed to restart: " + e.getMessage();
                    migration.markCompleted("stopped");
                    
                    return new MigrationResponse(
                        migration.id,
                        migration.taskUpid,
                        "Migration completed but VM failed to restart automatically",
                        vmId,
                        vm.name(),
                        currentNode,
                        request.targetNode(),
                        wasRunning ? "online" : "offline",
                        migration.startedAt
                    );
                }
            }
            
            // 7. Update migration record
            migration.markCompleted(migratedVm.status());
            
            return new MigrationResponse(
                migration.id,
                migration.taskUpid,
                "Migration completed successfully",
                vmId,
                vm.name(),
                currentNode,
                request.targetNode(),
                wasRunning ? "online" : "offline",
                migration.startedAt
            );
            
        } catch (Exception e) {
            log.error("Migration failed for VM {}: {}", vmId, e.getMessage());
            
            // Handle online migration failure
            if (wasRunning && !request.allowOfflineMigration()) {
                migration.markFailed("Online migration failed: " + e.getMessage());
                throw new RuntimeException(
                    "Online migration failed. Set allowOfflineMigration=true to retry with offline migration. Error: " + e.getMessage(), 
                    e
                );
            }
            
            // Try offline migration if allowed
            if (wasRunning && request.allowOfflineMigration()) {
                log.info("Online migration failed, attempting offline migration for VM {}", vmId);
                return performOfflineMigration(vm, request, migration, ticket);
            }
            
            // Migration failed for stopped VM
            migration.markFailed(e.getMessage());
            throw new RuntimeException("Migration failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Perform offline migration (stop VM, migrate, start VM)
     */
    private MigrationResponse performOfflineMigration(VMResponse vm, MigrationRequest request, 
                                                     VmMigration migration, String ticket) {
        String csrfToken = ticketManager.getCsrfToken();
        
        try {
            // Stop the VM first
            log.info("Stopping VM {} for offline migration", vm.vmid());
            vmService.stopVM(vm.node(), vm.vmid(), ticket);
            
            // Wait for VM to stop
            Thread.sleep(5000);
            
            // Now attempt offline migration
            log.info("Attempting offline migration for VM {} (with-local-disks not sent for offline)", vm.vmid());
            
            // For offline migration, use the original migration type
            String migrationType = request.migrationType();
            
            // TEMPORARY: Don't send migration_type to debug parameter verification issue
            log.info("DEBUG: Calling migrateVM for offline without migration_type parameter");
            
            TaskStatusResponse task = proxmoxClient.migrateVM(
                vm.node(),
                vm.vmid(),
                request.targetNode(),
                null, // offline - don't send online parameter
                null, // Don't send with-local-disks for offline migration
                request.force() ? 1 : null,
                request.bwlimit(),
                request.targetStorage(),
                null,  // TEMPORARY: Don't send migration_type
                request.migrationNetwork(),
                ticket,
                csrfToken
            );
            
            if (task.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox for offline migration");
            }
            
            migration.taskUpid = task.getData();
            migration.migrationType = "offline";
            migration.persist();
            
            // Wait for migration to complete
            TaskStatusDetailResponse taskStatus = waitForTaskCompletion(task.getData(), ticket);
            
            if (!"OK".equals(taskStatus.exitstatus())) {
                throw new RuntimeException("Offline migration task failed: " + taskStatus.exitstatus());
            }
            
            // Start the VM on the target node
            log.info("Starting VM {} on target node {}", vm.vmid(), request.targetNode());
            vmService.startVM(request.targetNode(), vm.vmid(), ticket);
            
            // Give it a moment to start
            Thread.sleep(3000);
            
            // Verify final state
            List<VMResponse> updatedVms = vmService.listVMs(ticket);
            VMResponse migratedVm = updatedVms.stream()
                .filter(v -> v.vmid() == vm.vmid())
                .findFirst()
                .orElse(null);
            
            if (migratedVm != null) {
                migration.markCompleted(migratedVm.status());
            } else {
                migration.markCompleted("unknown");
            }
            
            return new MigrationResponse(
                migration.id,
                migration.taskUpid,
                "Offline migration completed successfully",
                vm.vmid(),
                vm.name(),
                vm.node(),
                request.targetNode(),
                "offline",
                migration.startedAt
            );
            
        } catch (Exception e) {
            log.error("Offline migration failed for VM {}: {}", vm.vmid(), e.getMessage());
            migration.markFailed("Offline migration failed: " + e.getMessage());
            throw new RuntimeException("Offline migration failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check migration preconditions (mainly for bulk operations)
     */
    public MigrationPreconditionsResponse checkMigration(int vmId, String targetNode, @AuthTicket String ticket) {
        log.debug("Checking migration preconditions for VM {} to node {}", vmId, targetNode);
        
        try {
            // Get VM details to find current node
            List<VMResponse> vms = vmService.listVMs(ticket);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Check preconditions
            return proxmoxClient.checkMigrationPreconditions(vm.node(), vmId, targetNode, ticket);
            
        } catch (Exception e) {
            log.error("Failed to check migration preconditions: {}", e.getMessage());
            throw new RuntimeException("Failed to check migration preconditions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get migration history for a VM
     */
    public List<MigrationHistoryResponse> getMigrationHistory(int vmId) {
        log.debug("Getting migration history for VM {}", vmId);
        
        List<VmMigration> migrations = VmMigration.findByVmId(vmId);
        return migrations.stream()
            .map(MigrationHistoryResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all migrations for a specific node (as source or target)
     */
    public List<MigrationHistoryResponse> getNodeMigrations(String node, boolean asSource) {
        log.debug("Getting migrations for node {} as {}", node, asSource ? "source" : "target");
        
        List<VmMigration> migrations = asSource 
            ? VmMigration.findBySourceNode(node)
            : VmMigration.findByTargetNode(node);
            
        return migrations.stream()
            .map(MigrationHistoryResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Wait for a task to complete (no timeout - migrations can take hours)
     */
    private TaskStatusDetailResponse waitForTaskCompletion(String upid, String ticket) throws Exception {
        log.info("Waiting for task {} to complete (this may take a while for large disks)...", upid);
        
        int checkCount = 0;
        while (true) {
            TaskStatusDetailResponse status = taskService.getTaskStatus(upid, ticket);
            
            if (Boolean.TRUE.equals(status.finished())) {
                log.info("Task {} completed with status: {}", upid, status.exitstatus());
                return status;
            }
            
            // Log progress every 10 checks (20 seconds)
            if (++checkCount % 10 == 0) {
                log.info("Migration still in progress... (checked {} times)", checkCount);
            }
            
            // Wait 2 seconds before checking again
            Thread.sleep(2000);
        }
    }
    
    /**
     * Create migration record in database
     */
    private VmMigration createMigrationRecord(VMResponse vm, MigrationRequest request) {
        VmMigration migration = new VmMigration();
        migration.vmId = vm.vmid();
        migration.vmName = vm.name();
        migration.sourceNode = vm.node();
        migration.targetNode = request.targetNode();
        migration.migrationType = "running".equals(vm.status()) ? "online" : "offline";
        migration.preMigrationState = vm.status();
        migration.startedAt = Instant.now();
        migration.status = "started";
        migration.initiatedBy = "moxxie"; // TODO: Get from security context when available
        
        // Store migration options
        Map<String, Object> options = new HashMap<>();
        if (request.bwlimit() != null) options.put("bwlimit", request.bwlimit());
        if (request.targetStorage() != null) options.put("targetStorage", request.targetStorage());
        if (request.withLocalDisks()) options.put("withLocalDisks", true);
        if (request.force()) options.put("force", true);
        options.put("migrationType", request.migrationType());
        if (request.migrationNetwork() != null) options.put("migrationNetwork", request.migrationNetwork());
        migration.options = options;
        
        migration.persist();
        return migration;
    }
}