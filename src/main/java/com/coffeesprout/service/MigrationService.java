package com.coffeesprout.service;

import com.coffeesprout.api.dto.*;
import jakarta.enterprise.context.control.ActivateRequestContext;
import io.quarkus.virtual.threads.VirtualThreads;
import java.util.concurrent.ExecutorService;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.transaction.Transactional;
import com.coffeesprout.client.MigrationPreconditionsResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.StoragePool;
import com.coffeesprout.client.StorageResponse;
import com.coffeesprout.client.TaskStatusResponse;
import com.coffeesprout.config.MigrationConfig;
import com.coffeesprout.model.VmMigration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
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
    
    @Inject
    MigrationConfig migrationConfig;
    
    @Inject
    StorageConfigCache storageCache;
    
    @Inject
    @VirtualThreads
    ExecutorService executorService;
    
    /**
     * Check if a VM has local disks that need special migration handling
     * by querying storage configuration to check the 'shared' flag
     * 
     * @return LocalDiskDetectionResult containing detection details
     */
    private LocalDiskDetectionResult detectLocalDisks(int vmId, String node, String ticket) {
        if (!migrationConfig.autoDetectLocalDisks()) {
            log.debug("Auto-detection disabled by configuration");
            return new LocalDiskDetectionResult(false, null, null);
        }
        
        try {
            // Get VM configuration to find disk storages
            Map<String, Object> vmConfig = vmService.getVMConfig(node, vmId, ticket);
            List<String> diskStorages = extractDiskStorages(vmConfig);
            
            if (diskStorages.isEmpty()) {
                log.debug("VM {} has no disks to check", vmId);
                return new LocalDiskDetectionResult(false, List.of(), "no-disks");
            }
            
            log.debug("VM {} has disks on storage pools: {}", vmId, diskStorages);
            
            // Try to get storage configuration from cache first
            StorageResponse storageResponse = storageCache.getCached();
            if (storageResponse == null) {
                log.debug("Storage configuration not in cache, fetching from API");
                storageResponse = fetchStorageConfigWithRetry(ticket);
                if (storageResponse != null) {
                    storageCache.updateCache(storageResponse);
                }
            } else {
                log.debug("Using cached storage configuration");
            }
            
            if (storageResponse == null || storageResponse.getData() == null) {
                if (migrationConfig.useNamingFallback()) {
                    log.warn("Could not get storage configuration for VM {}, falling back to name-based detection", vmId);
                    List<String> localStorages = detectLocalByNaming(diskStorages);
                    return new LocalDiskDetectionResult(!localStorages.isEmpty(), localStorages, "naming-fallback");
                } else {
                    log.error("Could not get storage configuration and naming fallback is disabled");
                    throw new RuntimeException("Unable to determine storage type - storage query failed and fallback disabled");
                }
            }
            
            // Build a map of storage name to shared status
            Map<String, Boolean> storageSharedMap = new HashMap<>();
            List<String> localStorages = new ArrayList<>();
            
            for (StoragePool pool : storageResponse.getData()) {
                boolean isShared = pool.getShared() == 1;
                storageSharedMap.put(pool.getStorage(), isShared);
                
                // Log storage pool details at appropriate level
                String logMsg = String.format("Storage pool '%s' type='%s' shared=%s", 
                    pool.getStorage(), pool.getType(), isShared);
                if ("INFO".equals(migrationConfig.autoDetectionLogLevel())) {
                    log.info(logMsg);
                } else {
                    log.debug(logMsg);
                }
            }
            
            // Check if any disk is on non-shared (local) storage
            for (String storageName : diskStorages) {
                Boolean isShared = storageSharedMap.get(storageName);
                if (isShared != null && !isShared) {
                    localStorages.add(storageName);
                    log.info("VM {} has local disk on non-shared storage: {}", vmId, storageName);
                }
            }
            
            if (localStorages.isEmpty()) {
                log.info("VM {} has no local disks - all storage is shared", vmId);
            } else {
                log.info("VM {} has {} local disk(s) on storage: {}", vmId, localStorages.size(), localStorages);
            }
            
            return new LocalDiskDetectionResult(!localStorages.isEmpty(), localStorages, "storage-api");
            
        } catch (Exception e) {
            log.error("Failed to check VM {} for local disks: {}", vmId, e.getMessage(), e);
            
            if (migrationConfig.useNamingFallback()) {
                log.warn("Attempting naming-based fallback due to error");
                try {
                    Map<String, Object> vmConfig = vmService.getVMConfig(node, vmId, ticket);
                    List<String> diskStorages = extractDiskStorages(vmConfig);
                    List<String> localStorages = detectLocalByNaming(diskStorages);
                    return new LocalDiskDetectionResult(!localStorages.isEmpty(), localStorages, "naming-fallback-error");
                } catch (Exception fallbackError) {
                    log.error("Fallback also failed: {}", fallbackError.getMessage());
                }
            }
            
            // If we can't determine, assume no local disks to avoid blocking migration
            return new LocalDiskDetectionResult(false, null, "error-default");
        }
    }
    
    /**
     * Helper class to hold local disk detection results
     */
    public record LocalDiskDetectionResult(
        boolean hasLocalDisks,
        List<String> localStoragePools,
        String detectionMethod
    ) {}
    
    /**
     * Fetch storage configuration with retry logic
     */
    private StorageResponse fetchStorageConfigWithRetry(String ticket) {
        int maxRetries = migrationConfig.storageQueryMaxRetries();
        Exception lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i > 0) {
                    log.debug("Retrying storage configuration query (attempt {}/{})", i + 1, maxRetries);
                    Thread.sleep(1000); // Wait 1 second between retries
                }
                
                StorageResponse response = proxmoxClient.getStorage(ticket);
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                lastException = e;
                log.debug("Storage query attempt {} failed: {}", i + 1, e.getMessage());
            }
        }
        
        if (lastException != null) {
            log.error("All {} attempts to query storage configuration failed. Last error: {}", 
                     maxRetries, lastException.getMessage());
        }
        return null;
    }
    
    /**
     * Extract storage names from VM disk configuration
     */
    private List<String> extractDiskStorages(Map<String, Object> config) {
        List<String> storages = new ArrayList<>();
        
        // Look for disk configurations (scsi0, ide0, virtio0, etc.)
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (key.matches("(scsi|ide|sata|virtio|efidisk|tpmstate)\\d+")) {
                String diskConfig = String.valueOf(entry.getValue());
                // Extract storage name from config like "local-zfs:vm-8200-disk-0,format=raw,size=20G"
                int colonIndex = diskConfig.indexOf(':');
                if (colonIndex > 0) {
                    String storage = diskConfig.substring(0, colonIndex);
                    if (!storages.contains(storage)) {
                        storages.add(storage);
                    }
                }
            }
        }
        
        return storages;
    }
    
    /**
     * Fallback method using naming conventions if storage query fails
     * Returns list of storage pools that appear to be local based on naming
     */
    private List<String> detectLocalByNaming(List<String> storages) {
        List<String> localStorages = new ArrayList<>();
        List<String> patterns = migrationConfig.localStoragePatterns();
        
        for (String storage : storages) {
            if (storage != null) {
                for (String pattern : patterns) {
                    if (storage.toLowerCase().contains(pattern.toLowerCase())) {
                        log.info("Storage '{}' matches local pattern '{}' (name-based detection)", storage, pattern);
                        localStorages.add(storage);
                        break;
                    }
                }
            }
        }
        
        return localStorages;
    }
    
    /**
     * Start VM migration asynchronously - returns immediately with task info
     * Use this for API calls to avoid HTTP timeouts on long migrations
     */
    public MigrationStartResponse startMigrationAsync(int vmId, MigrationRequest request, @AuthTicket String ticket) {
        // First create the migration record in a transaction
        MigrationStartInfo startInfo = initiateMigration(vmId, request, ticket);
        
        // Then start the async monitoring (outside transaction, after commit)
        startAsyncMonitoring(startInfo, request, ticket);
        
        // Return response immediately
        String statusUrl = "/api/v1/vms/" + vmId + "/migrate/status/" + startInfo.migrationId();
        
        return new MigrationStartResponse(
            startInfo.migrationId(),
            startInfo.taskUpid(),
            "Migration task started successfully",
            vmId,
            startInfo.sourceNode(),
            startInfo.targetNode(),
            statusUrl
        );
    }
    
    /**
     * Create migration record and start the task - this is transactional
     */
    @Transactional
    public MigrationStartInfo initiateMigration(int vmId, MigrationRequest request, @AuthTicket String ticket) {
        log.info("Initiating migration of VM {} to node {}", vmId, request.targetNode());
        
        // 1. Get VM details to find current node
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
        
        // Validate target node is different
        if (currentNode.equals(request.targetNode())) {
            throw new RuntimeException("VM is already on node " + currentNode);
        }
        
        // 2. Create migration history record
        VmMigration migration = createMigrationRecord(vm, request);
        
        // 3. Auto-detect if we need to migrate with local disks
        LocalDiskDetectionResult detectionResult = null;
        boolean needsLocalDiskMigration = false;
        
        if (request.withLocalDisks() != null) {
            needsLocalDiskMigration = request.withLocalDisks();
            log.info("Using explicit withLocalDisks setting: {}", needsLocalDiskMigration);
        } else {
            detectionResult = detectLocalDisks(vmId, currentNode, ticket);
            needsLocalDiskMigration = detectionResult.hasLocalDisks();
            log.info("Auto-detected local disks: {}", needsLocalDiskMigration);
        }
        
        // 4. Start migration task
        String csrfToken = ticketManager.getCsrfToken();
        
        try {
            boolean wasRunning = "running".equals(vm.status());
            Integer online = wasRunning ? 1 : null;
            
            log.info("Starting {} migration task for VM {} from {} to {}", 
                wasRunning ? "online" : "offline", vmId, currentNode, request.targetNode());
            
            // Start migration
            TaskStatusResponse task = proxmoxClient.migrateVM(
                currentNode,
                vmId,
                request.targetNode(),
                online,
                needsLocalDiskMigration ? 1 : null,
                request.force() ? 1 : null,
                request.bwlimit(),
                request.targetStorage(),
                null,  // migration_type temporarily disabled
                request.migrationNetwork(),
                ticket,
                csrfToken
            );
            
            if (task.getData() == null) {
                migration.markFailed("No task ID returned from Proxmox");
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            migration.taskUpid = task.getData();
            migration.persist();
            migration.flush(); // Force flush to ensure it's persisted
            
            log.info("Migration task {} started for VM {}", task.getData(), vmId);
            
            // Return info for async monitoring
            return new MigrationStartInfo(
                migration.id,
                task.getData(),
                vmId,
                vm.name(),
                currentNode,
                request.targetNode(),
                wasRunning,
                detectionResult != null,
                needsLocalDiskMigration,
                detectionResult
            );
            
        } catch (Exception e) {
            log.error("Failed to start migration for VM {}: {}", vmId, e.getMessage());
            migration.markFailed(e.getMessage());
            throw new RuntimeException("Failed to start migration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start async monitoring of migration - called after transaction commits
     */
    private void startAsyncMonitoring(MigrationStartInfo startInfo, MigrationRequest request, String ticket) {
        executorService.submit(() -> {
            log.info("Starting async migration monitoring for task {}", startInfo.taskUpid());
            try {
                // This runs in a virtual thread and can take hours
                completeMigrationAsync(
                    startInfo.migrationId(),
                    startInfo.vmId(),
                    startInfo.taskUpid(),
                    startInfo.wasRunning(),
                    startInfo.wasAutoDetected(),
                    startInfo.needsLocalDiskMigration(),
                    startInfo.detectionResult(),
                    request,
                    ticket
                );
            } catch (Exception e) {
                log.error("Error in async migration completion for VM {}: {}", startInfo.vmId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Complete migration asynchronously - this runs in a virtual thread
     * and can take hours without blocking
     */
    @ActivateRequestContext
    void completeMigrationAsync(Long migrationId, int vmId, String taskUpid, 
                                       boolean wasRunning, boolean wasAutoDetected,
                                       boolean needsLocalDiskMigration,
                                       LocalDiskDetectionResult detectionResult,
                                       MigrationRequest request, String ticket) {
        log.info("Monitoring migration task {} for VM {} (migration ID: {})", taskUpid, vmId, migrationId);
        
        // Load the migration record
        VmMigration migration = VmMigration.findById(migrationId);
        if (migration == null) {
            log.error("Migration record {} not found", migrationId);
            return;
        }
        
        try {
            // Wait for task completion (no timeout - migrations can take hours)
            TaskStatusDetailResponse taskStatus = waitForTaskCompletion(taskUpid, ticket);
            
            if (!"OK".equals(taskStatus.exitstatus())) {
                migration.markFailed("Migration task failed: " + taskStatus.exitstatus());
                log.error("Migration task {} failed with status: {}", taskUpid, taskStatus.exitstatus());
                return;
            }
            
            // Verify VM is on target node
            Thread.sleep(2000);
            List<VMResponse> updatedVms = vmService.listVMs(ticket);
            VMResponse migratedVm = updatedVms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (migratedVm == null) {
                migration.markFailed("VM not found after migration");
                return;
            }
            
            if (!request.targetNode().equals(migratedVm.node())) {
                migration.markFailed("VM not on target node after migration");
                return;
            }
            
            // Handle state recovery if needed
            if (wasRunning && !"running".equals(migratedVm.status())) {
                log.info("VM {} was running before migration but stopped after. Attempting to start...", vmId);
                try {
                    vmService.startVM(request.targetNode(), vmId, ticket);
                    Thread.sleep(3000);
                } catch (Exception e) {
                    log.error("Failed to restart VM {} after migration: {}", vmId, e.getMessage());
                    migration.postMigrationState = "stopped";
                    migration.errorMessage = "Migration succeeded but VM failed to restart: " + e.getMessage();
                }
            }
            
            migration.markCompleted(migratedVm.status());
            log.info("Migration {} completed successfully for VM {}", migrationId, vmId);
            
        } catch (Exception e) {
            log.error("Error monitoring migration {}: {}", migrationId, e.getMessage(), e);
            migration.markFailed("Error during migration: " + e.getMessage());
        }
    }
    
    /**
     * Migrate a VM to a target node (synchronous - waits for completion)
     * Note: Not transactional as migrations can take up to an hour
     * This method is kept for backward compatibility but should be avoided for production use
     */
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
        
        // 3. Auto-detect if we need to migrate with local disks
        boolean needsLocalDiskMigration = false;
        LocalDiskDetectionResult detectionResult = null;
        boolean wasAutoDetected = false;
        
        if (request.withLocalDisks() != null) {
            // User explicitly specified the option
            needsLocalDiskMigration = request.withLocalDisks();
            log.info("Using explicit withLocalDisks setting: {}", needsLocalDiskMigration);
        } else {
            // Auto-detect based on VM configuration and storage settings
            detectionResult = detectLocalDisks(vmId, currentNode, ticket);
            needsLocalDiskMigration = detectionResult.hasLocalDisks();
            wasAutoDetected = true;
            
            if (needsLocalDiskMigration) {
                log.info("Auto-detected VM {} has local disks on {} storage pool(s) using {} - enabling withLocalDisks option", 
                        vmId, detectionResult.localStoragePools().size(), detectionResult.detectionMethod());
            } else {
                log.info("VM {} has no local disks (detection method: {}) - standard migration", 
                        vmId, detectionResult.detectionMethod());
            }
        }
        
        // 4. Attempt migration directly
        String csrfToken = ticketManager.getCsrfToken();
        
        try {
            // Default to online migration if VM is running
            Integer online = wasRunning ? 1 : null;
            
            log.info("Attempting {} migration for VM {} from {} to {}", 
                wasRunning ? "online" : "offline", vmId, currentNode, request.targetNode());
            
            // Log parameters for debugging
            log.info("Migration parameters - online: {}, withLocalDisks: {}, force: {}, bwlimit: {}, targetStorage: {}, migrationType: {}, migrationNetwork: {}",
                online, needsLocalDiskMigration ? 1 : null, request.force() ? 1 : null, 
                request.bwlimit(), request.targetStorage(), request.migrationType(), request.migrationNetwork());
            
            // Execute migration with proper parameters
            // When migrating with local disks, use insecure migration type
            String migrationType = request.migrationType();
            if (needsLocalDiskMigration && "secure".equals(migrationType)) {
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
                needsLocalDiskMigration ? 1 : null,  // Auto-detected or user-specified
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
                        migration.startedAt,
                        wasAutoDetected ? needsLocalDiskMigration : null,
                        detectionResult != null ? detectionResult.localStoragePools() : null,
                        detectionResult != null ? detectionResult.detectionMethod() : null
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
                migration.startedAt,
                wasAutoDetected ? needsLocalDiskMigration : null,
                detectionResult != null ? detectionResult.localStoragePools() : null,
                detectionResult != null ? detectionResult.detectionMethod() : null
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
     * Get current migration status by migration ID
     */
    public MigrationHistoryResponse getMigrationStatus(Long migrationId) {
        log.debug("Getting migration status for migration {}", migrationId);
        
        VmMigration migration = VmMigration.findById(migrationId);
        if (migration == null) {
            return null;
        }
        
        return MigrationHistoryResponse.fromEntity(migration);
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
        if (request.withLocalDisks() != null && request.withLocalDisks()) options.put("withLocalDisks", true);
        if (request.force() != null && request.force()) options.put("force", true);
        options.put("migrationType", request.migrationType());
        if (request.migrationNetwork() != null) options.put("migrationNetwork", request.migrationNetwork());
        migration.options = options;
        
        migration.persist();
        return migration;
    }
}