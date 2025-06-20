package com.coffeesprout.service;

import com.coffeesprout.api.dto.BackupRequest;
import com.coffeesprout.api.dto.BackupResponse;
import com.coffeesprout.api.dto.BulkBackupRequest;
import com.coffeesprout.api.dto.BulkBackupResponse;
import com.coffeesprout.api.dto.RestoreRequest;
import com.coffeesprout.api.dto.TaskResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.StorageContent;
import com.coffeesprout.client.StorageContentResponse;
import com.coffeesprout.client.TaskStatusResponse;
import com.coffeesprout.client.NodesResponse;
import com.coffeesprout.client.StorageResponse;
import com.coffeesprout.scheduler.service.VMSelectorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class BackupService {
    
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    
    // Pattern to extract VM ID from backup filename
    private static final Pattern BACKUP_FILENAME_PATTERN = 
        Pattern.compile("vzdump-qemu-(\\d+)-\\d{4}_\\d{2}_\\d{2}-\\d{2}_\\d{2}_\\d{2}\\..*");
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    VMService vmService;
    
    @Inject
    NodeService nodeService;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    VMSelectorService vmSelectorService;
    
    /**
     * Create a backup for a VM
     */
    public TaskResponse createBackup(int vmId, BackupRequest request, @AuthTicket String ticket) {
        log.info("Creating backup for VM {} to storage '{}'", vmId, request.storage());
        
        try {
            // Get VM info to find its node
            var vms = vmService.listVMs(ticket);
            var vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Create backup with minimal required parameters
            TaskStatusResponse response = proxmoxClient.createBackup(
                    vm.node(),
                    String.valueOf(vmId),
                    request.storage(),
                    request.mode() != null ? request.mode() : "snapshot",
                    request.compress() != null ? request.compress() : "zstd",
                    null, // notes - omit for now
                    null, // protected flag 
                    null, // removeOlder
                    null, // mailnotification
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("Backup task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    String.format("Backup of VM %d to storage '%s' started", vmId, request.storage()));
                    
        } catch (Exception e) {
            log.error("Failed to create backup for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to create backup: " + e.getMessage(), e);
        }
    }
    
    /**
     * List all backups for a specific VM
     */
    public List<BackupResponse> listBackups(int vmId, @AuthTicket String ticket) {
        log.debug("Listing backups for VM {}", vmId);
        
        try {
            List<BackupResponse> allBackups = new ArrayList<>();
            
            // Get all nodes
            var nodes = nodeService.listNodes(ticket);
            
            // Get all storage locations that might contain backups
            for (var node : nodes) {
                try {
                    // Get storage pools for this node
                    StorageResponse storageResponse = proxmoxClient.getNodeStorage(node.getName(), ticket);
                    if (storageResponse.getData() == null) {
                        continue;
                    }
                    
                    // Check each storage for backups
                    for (var storage : storageResponse.getData()) {
                        if (storage.getContent() != null && storage.getContent().contains("backup")) {
                            try {
                                // List content of this storage filtered by VM ID
                                StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                                        node.getName(),
                                        storage.getStorage(),
                                        "backup",
                                        vmId,
                                        ticket
                                );
                                
                                if (contentResponse.getData() != null) {
                                    // Convert to BackupResponse objects
                                    List<BackupResponse> backups = contentResponse.getData().stream()
                                            .filter(StorageContent::isBackup)
                                            .filter(content -> content.getVmid() != null && content.getVmid() == vmId)
                                            .map(content -> convertToBackupResponse(content, node.getName()))
                                            .collect(Collectors.toList());
                                    
                                    allBackups.addAll(backups);
                                }
                            } catch (Exception e) {
                                log.debug("Failed to list content for storage {} on node {}: {}", 
                                         storage.getStorage(), node.getName(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to process node {}: {}", node.getName(), e.getMessage());
                }
            }
            
            // Sort by creation time (newest first)
            allBackups.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
            
            return allBackups;
                    
        } catch (Exception e) {
            log.error("Failed to list backups for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to list backups: " + e.getMessage(), e);
        }
    }
    
    /**
     * List all backups across all VMs
     */
    public List<BackupResponse> listAllBackups(@AuthTicket String ticket) {
        log.info("Listing all backups across all nodes and storages");
        
        try {
            List<BackupResponse> allBackups = new ArrayList<>();
            Set<String> processedSharedStorages = new HashSet<>();
            
            // Get all nodes
            var nodes = nodeService.listNodes(ticket);
            log.info("Found {} nodes to check for backups", nodes.size());
            
            // Process each node
            for (var node : nodes) {
                log.info("Checking node {} for backup storages", node.getName());
                try {
                    // Get storage pools for this node
                    StorageResponse storageResponse = proxmoxClient.getNodeStorage(node.getName(), ticket);
                    if (storageResponse.getData() == null) {
                        continue;
                    }
                    
                    // Check each storage for backups
                    log.info("Node {} has {} storage pools", node.getName(), storageResponse.getData().size());
                    for (var storage : storageResponse.getData()) {
                        if (storage.getContent() != null && storage.getContent().contains("backup")) {
                            // Check if this is a shared storage that we've already processed
                            boolean isShared = storage.getShared() == 1;
                            if (isShared && processedSharedStorages.contains(storage.getStorage())) {
                                log.info("Skipping shared storage {} on node {} (already processed)", 
                                        storage.getStorage(), node.getName());
                                continue;
                            }
                            
                            log.info("Storage {} on node {} supports backups (shared: {})", 
                                    storage.getStorage(), node.getName(), isShared);
                            
                            try {
                                // List all backup content in this storage
                                StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                                        node.getName(),
                                        storage.getStorage(),
                                        "backup",
                                        null, // No VM ID filter
                                        ticket
                                );
                                
                                if (contentResponse.getData() != null) {
                                    // Convert to BackupResponse objects
                                    List<BackupResponse> backups = contentResponse.getData().stream()
                                            .filter(StorageContent::isBackup)
                                            .map(content -> convertToBackupResponse(content, node.getName()))
                                            .collect(Collectors.toList());
                                    
                                    log.info("Found {} backups in storage {} on node {}", 
                                             backups.size(), storage.getStorage(), node.getName());
                                    allBackups.addAll(backups);
                                    
                                    // Mark shared storage as processed
                                    if (isShared) {
                                        processedSharedStorages.add(storage.getStorage());
                                    }
                                } else {
                                    log.info("No backup data returned for storage {} on node {}", 
                                             storage.getStorage(), node.getName());
                                }
                            } catch (Exception e) {
                                log.debug("Failed to list content for storage {} on node {}: {}", 
                                         storage.getStorage(), node.getName(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to process node {}: {}", node.getName(), e.getMessage());
                }
            }
            
            // Sort by creation time (newest first)
            allBackups.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
            
            return allBackups;
                    
        } catch (Exception e) {
            log.error("Failed to list all backups: {}", e.getMessage());
            throw new RuntimeException("Failed to list backups: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete a backup
     */
    public TaskResponse deleteBackup(String volid, @AuthTicket String ticket) {
        log.info("Deleting backup: {}", volid);
        
        try {
            // Parse volume ID to extract storage and volume path
            String[] parts = volid.split(":", 2);
            if (parts.length != 2) {
                throw new RuntimeException("Invalid volume ID format: " + volid);
            }
            
            String storage = parts[0];
            String volumePath = parts[1];
            
            // Find which node has this storage
            String targetNode = findNodeWithStorage(storage, ticket);
            if (targetNode == null) {
                throw new RuntimeException("Storage not found on any node: " + storage);
            }
            
            // Check if backup is protected
            StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                    targetNode,
                    storage,
                    "backup",
                    null,
                    ticket
            );
            
            if (contentResponse.getData() != null) {
                var backup = contentResponse.getData().stream()
                        .filter(content -> volid.equals(content.getVolid()))
                        .findFirst()
                        .orElse(null);
                
                if (backup != null && backup.isProtected()) {
                    throw new RuntimeException("Cannot delete protected backup: " + volid);
                }
            }
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Delete the backup (JAX-RS will handle URL encoding)
            TaskStatusResponse response = proxmoxClient.deleteBackup(
                    targetNode,
                    storage,
                    volumePath,
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("Backup deletion task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    "Backup deletion started: " + volid);
                    
        } catch (Exception e) {
            log.error("Failed to delete backup {}: {}", volid, e.getMessage());
            throw new RuntimeException("Failed to delete backup: " + e.getMessage(), e);
        }
    }
    
    /**
     * Restore a VM from backup
     */
    public TaskResponse restoreBackup(RestoreRequest request, @AuthTicket String ticket) {
        log.info("Restoring VM {} from backup: {}", request.vmId(), request.backup());
        
        try {
            // Check if target VM ID already exists if not forcing overwrite
            if (!request.overwriteExisting()) {
                var vms = vmService.listVMs(ticket);
                boolean vmExists = vms.stream()
                        .anyMatch(vm -> vm.vmid() == request.vmId());
                
                if (vmExists) {
                    throw new RuntimeException("VM with ID " + request.vmId() + 
                            " already exists. Set overwriteExisting=true to replace it.");
                }
            }
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Restore the VM
            TaskStatusResponse response = proxmoxClient.restoreVM(
                    request.targetNode(),
                    request.vmId(),
                    request.backup(),
                    request.targetStorage(),
                    request.name(),
                    request.description(),
                    request.startAfterRestore() ? 1 : 0,
                    request.unique() ? 1 : 0,
                    request.bandwidth(),
                    request.overwriteExisting() ? 1 : 0,
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("VM restore task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    String.format("Restore of VM %d from backup '%s' started on node %s", 
                                  request.vmId(), request.backup(), request.targetNode()));
                    
        } catch (Exception e) {
            log.error("Failed to restore VM from backup: {}", e.getMessage());
            throw new RuntimeException("Failed to restore VM: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find which node has a specific storage
     */
    private String findNodeWithStorage(String storageId, @AuthTicket String ticket) {
        var nodes = nodeService.listNodes(ticket);
        
        for (var node : nodes) {
            try {
                StorageResponse storageResponse = proxmoxClient.getNodeStorage(node.getName(), ticket);
                if (storageResponse.getData() != null) {
                    boolean hasStorage = storageResponse.getData().stream()
                            .anyMatch(storage -> storageId.equals(storage.getStorage()));
                    
                    if (hasStorage) {
                        return node.getName();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to check storage on node {}: {}", node.getName(), e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Convert StorageContent to BackupResponse
     */
    private BackupResponse convertToBackupResponse(StorageContent content, String node) {
        // Extract compression type from filename or format
        String compression = extractCompression(content);
        
        // Extract VM ID from filename if not set
        Integer vmId = content.getVmid();
        if (vmId == null) {
            vmId = extractVmIdFromFilename(content.getFilename());
        }
        
        return new BackupResponse(
                content.getVolid(),
                content.getFilename(),
                content.getSize(),
                Instant.ofEpochSecond(content.getCtime()),
                content.getNotes(),
                content.isProtected(),
                vmId,
                node,
                compression,
                false, // Proxmox doesn't provide encryption info in storage content
                content.getVerification() != null ? content.getVerification().getState() : "none",
                content.getStorageId(),
                BackupResponse.formatSize(content.getSize())
        );
    }
    
    /**
     * Extract compression type from backup content
     */
    private String extractCompression(StorageContent content) {
        String filename = content.getFilename();
        if (filename != null) {
            if (filename.endsWith(".zst")) return "zstd";
            if (filename.endsWith(".gz")) return "gzip";
            if (filename.endsWith(".lzo")) return "lzo";
            if (filename.endsWith(".vma")) return "none";
        }
        return content.getSubtype() != null ? content.getSubtype() : "unknown";
    }
    
    /**
     * Extract VM ID from backup filename
     */
    private Integer extractVmIdFromFilename(String filename) {
        if (filename != null) {
            Matcher matcher = BACKUP_FILENAME_PATTERN.matcher(filename);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse VM ID from filename: {}", filename);
                }
            }
        }
        return null;
    }
    
    /**
     * Perform bulk backup operations on multiple VMs
     */
    public BulkBackupResponse bulkCreateBackups(BulkBackupRequest request, @AuthTicket String ticket) {
        log.info("Starting bulk backup operation with {} selectors to storage '{}'", 
                request.vmSelectors().size(), request.storage());
        
        Instant startTime = Instant.now();
        
        // Get all VMs that match the selectors
        List<VMResponse> targetVMs = new ArrayList<>();
        for (var selector : request.vmSelectors()) {
            try {
                List<VMResponse> vms = vmSelectorService.selectVMs(selector, ticket);
                targetVMs.addAll(vms);
            } catch (Exception e) {
                log.error("Failed to select VMs with selector {}: {}", selector, e.getMessage());
                throw new RuntimeException("Failed to select VMs: " + e.getMessage(), e);
            }
        }
        
        // Remove duplicates
        Map<Integer, VMResponse> uniqueVMs = new HashMap<>();
        for (VMResponse vm : targetVMs) {
            uniqueVMs.put(vm.vmid(), vm);
        }
        targetVMs = new ArrayList<>(uniqueVMs.values());
        
        log.info("Found {} unique VMs matching selectors", targetVMs.size());
        
        if (targetVMs.isEmpty()) {
            Instant endTime = Instant.now();
            return new BulkBackupResponse(
                Map.of(),
                "No VMs found matching the provided selectors",
                0, 0, 0, 0,
                request.dryRun(),
                startTime,
                endTime,
                java.time.Duration.between(startTime, endTime).getSeconds()
            );
        }
        
        // Check if storage exists (do this once for all VMs)
        if (!request.dryRun()) {
            try {
                // Verify storage exists by getting storage info
                var nodes = nodeService.listNodes(ticket);
                if (!nodes.isEmpty()) {
                    // Just check on the first node - storage should be available cluster-wide
                    StorageContentResponse storageCheck = proxmoxClient.listStorageContent(
                        nodes.get(0).getName(),
                        request.storage(),
                        null,
                        null,
                        ticket
                    );
                    log.debug("Storage '{}' verified", request.storage());
                }
            } catch (Exception e) {
                log.error("Storage verification failed for '{}': {}", request.storage(), e.getMessage());
                throw new RuntimeException("Storage '" + request.storage() + "' not accessible: " + e.getMessage());
            }
        }
        
        // Prepare results map - use concurrent map for thread safety
        Map<Integer, BulkBackupResponse.BackupResult> results = new ConcurrentHashMap<>();
        
        // If dry run, just show what would be done
        if (request.dryRun()) {
            for (VMResponse vm : targetVMs) {
                // Check if VM is running and mode requires stop/suspend
                if ("stop".equals(request.mode()) && "running".equals(vm.status())) {
                    results.put(vm.vmid(), BulkBackupResponse.BackupResult.dryRun(
                        vm.name() + " (would stop VM)", vm.node(), request.storage()
                    ));
                } else if ("suspend".equals(request.mode()) && "running".equals(vm.status())) {
                    results.put(vm.vmid(), BulkBackupResponse.BackupResult.dryRun(
                        vm.name() + " (would suspend VM)", vm.node(), request.storage()
                    ));
                } else {
                    results.put(vm.vmid(), BulkBackupResponse.BackupResult.dryRun(
                        vm.name(), vm.node(), request.storage()
                    ));
                }
            }
            
            Instant endTime = Instant.now();
            return new BulkBackupResponse(
                results,
                String.format("Dry run: Would create %d backups on storage '%s'", 
                    targetVMs.size(), request.storage()),
                targetVMs.size(),
                targetVMs.size(),
                0,
                0,
                true,
                startTime,
                endTime,
                java.time.Duration.between(startTime, endTime).getSeconds()
            );
        }
        
        // Create executor for parallel backup operations
        ExecutorService executor = Executors.newFixedThreadPool(request.maxParallel());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Get CSRF token once for all operations
        String csrfToken = ticketManager.getCsrfToken();
        
        for (VMResponse vm : targetVMs) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.info("Creating backup for VM {} ({}) on node {} to storage '{}'", 
                        vm.vmid(), vm.name(), vm.node(), request.storage());
                    
                    // Build notes with VM info
                    String notes = request.notes();
                    if (notes == null || notes.isBlank()) {
                        notes = String.format("Bulk backup of VM %s (%d)", vm.name(), vm.vmid());
                    }
                    
                    // Append TTL to notes if specified
                    if (request.ttlDays() != null && request.ttlDays() > 0) {
                        notes += String.format(" (TTL: %dd)", request.ttlDays());
                    }
                    
                    // Debug log the notes
                    log.debug("Backup notes for VM {}: {}", vm.vmid(), notes);
                    
                    // Create backup with notes (using notes-template parameter)
                    TaskStatusResponse response = proxmoxClient.createBackup(
                        vm.node(),
                        String.valueOf(vm.vmid()),
                        request.storage(),
                        request.mode(),
                        request.compress(),
                        notes,  // Now using correct notes-template parameter
                        null,  // protected flag - omit for now
                        null,  // removeOlder - omit for now
                        null,  // mailNotification - omit for now
                        ticket,
                        csrfToken
                    );
                    
                    if (response.getData() == null) {
                        throw new RuntimeException("No task ID returned from Proxmox");
                    }
                    
                    results.put(vm.vmid(), BulkBackupResponse.BackupResult.success(
                        response.getData(), vm.name(), vm.node(), request.storage()
                    ));
                    
                    log.info("Backup task {} started for VM {} ({})", 
                        response.getData(), vm.vmid(), vm.name());
                    
                } catch (Exception e) {
                    log.error("Failed to create backup for VM {} ({}): {}", 
                        vm.vmid(), vm.name(), e.getMessage());
                    results.put(vm.vmid(), BulkBackupResponse.BackupResult.error(
                        e.getMessage(), vm.name(), vm.node()
                    ));
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all backup tasks to be submitted
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            log.error("Error waiting for bulk backup completion: {}", e.getMessage());
        } finally {
            executor.shutdown();
        }
        
        // Count results
        long successCount = results.values().stream()
            .filter(r -> "success".equals(r.status()))
            .count();
        long failureCount = results.values().stream()
            .filter(r -> "error".equals(r.status()))
            .count();
        long skippedCount = results.values().stream()
            .filter(r -> "skipped".equals(r.status()))
            .count();
        
        String summary = String.format("Started %d/%d backup tasks on storage '%s'",
            successCount, targetVMs.size(), request.storage());
        if (failureCount > 0) {
            summary += String.format(" (%d failed)", failureCount);
        }
        
        Instant endTime = Instant.now();
        return new BulkBackupResponse(
            results,
            summary,
            targetVMs.size(),
            (int) successCount,
            (int) failureCount,
            (int) skippedCount,
            false,
            startTime,
            endTime,
            java.time.Duration.between(startTime, endTime).getSeconds()
        );
    }
}