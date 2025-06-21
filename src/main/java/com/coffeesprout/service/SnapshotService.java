package com.coffeesprout.service;

import com.coffeesprout.api.dto.BulkSnapshotRequest;
import com.coffeesprout.api.dto.BulkSnapshotResponse;
import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.TaskResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.Snapshot;
import com.coffeesprout.client.SnapshotsResponse;
import com.coffeesprout.client.TaskStatusResponse;
import com.coffeesprout.scheduler.model.VMSelector;
import com.coffeesprout.scheduler.service.VMSelectorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@AutoAuthenticate
public class SnapshotService {
    
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    VMService vmService;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    VMSelectorService vmSelectorService;
    
    /**
     * List all snapshots for a VM
     */
    public List<SnapshotResponse> listSnapshots(int vmId, @AuthTicket String ticket) {
        log.debug("Listing snapshots for VM {}", vmId);
        
        try {
            // Get VM info to find its node
            var vms = vmService.listVMs(ticket);
            var vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Get snapshots from Proxmox
            SnapshotsResponse response = proxmoxClient.listSnapshots(vm.node(), vmId, ticket);
            
            if (response.getData() == null) {
                return new ArrayList<>();
            }
            
            // Convert to API response format
            return response.getData().stream()
                    .filter(snap -> !"current".equals(snap.getName())) // Filter out the "current" pseudo-snapshot
                    .map(this::convertSnapshot)
                    .toList();
                    
        } catch (Exception e) {
            log.error("Failed to list snapshots for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to list snapshots: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new snapshot
     */
    public TaskResponse createSnapshot(int vmId, CreateSnapshotRequest request, @AuthTicket String ticket) {
        log.info("Creating snapshot '{}' for VM {}", request.name(), vmId);
        
        try {
            // Get VM info to find its node
            var vms = vmService.listVMs(ticket);
            var vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Validate snapshot name doesn't already exist
            var existingSnapshots = listSnapshots(vmId, ticket);
            if (existingSnapshots.stream().anyMatch(s -> s.name().equals(request.name()))) {
                throw new RuntimeException("Snapshot with name '" + request.name() + "' already exists");
            }
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Append TTL to description if specified
            String description = request.description();
            if (request.ttlHours() != null && request.ttlHours() > 0) {
                description = (description != null ? description : "") + " (TTL: " + request.ttlHours() + "h)";
            }
            
            // Create snapshot
            TaskStatusResponse response = proxmoxClient.createSnapshot(
                    vm.node(),
                    vmId,
                    request.name(),
                    description,
                    request.includeVmState() ? 1 : 0,
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("Snapshot creation task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    "Snapshot '" + request.name() + "' creation started for VM " + vmId);
                    
        } catch (Exception e) {
            log.error("Failed to create snapshot for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to create snapshot: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete a snapshot
     */
    public TaskResponse deleteSnapshot(int vmId, String snapshotName, @AuthTicket String ticket) {
        log.info("Deleting snapshot '{}' for VM {}", snapshotName, vmId);
        
        try {
            // Get VM info to find its node
            var vms = vmService.listVMs(ticket);
            var vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Verify snapshot exists
            var snapshots = listSnapshots(vmId, ticket);
            if (snapshots.stream().noneMatch(s -> s.name().equals(snapshotName))) {
                throw new RuntimeException("Snapshot not found: " + snapshotName);
            }
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Delete snapshot
            TaskStatusResponse response = proxmoxClient.deleteSnapshot(
                    vm.node(),
                    vmId,
                    snapshotName,
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("Snapshot deletion task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    "Snapshot '" + snapshotName + "' deletion started for VM " + vmId);
                    
        } catch (Exception e) {
            log.error("Failed to delete snapshot for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to delete snapshot: " + e.getMessage(), e);
        }
    }
    
    /**
     * Rollback VM to a snapshot
     */
    public TaskResponse rollbackSnapshot(int vmId, String snapshotName, @AuthTicket String ticket) {
        log.info("Rolling back VM {} to snapshot '{}'", vmId, snapshotName);
        
        try {
            // Get VM info to find its node
            var vms = vmService.listVMs(ticket);
            var vm = vms.stream()
                    .filter(v -> v.vmid() == vmId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("VM not found: " + vmId));
            
            // Verify snapshot exists
            var snapshots = listSnapshots(vmId, ticket);
            if (snapshots.stream().noneMatch(s -> s.name().equals(snapshotName))) {
                throw new RuntimeException("Snapshot not found: " + snapshotName);
            }
            
            // VM should be stopped for rollback (best practice)
            if ("running".equals(vm.status())) {
                log.warn("VM {} is running. Rollback works best with stopped VMs", vmId);
            }
            
            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();
            
            // Rollback to snapshot
            TaskStatusResponse response = proxmoxClient.rollbackSnapshot(
                    vm.node(),
                    vmId,
                    snapshotName,
                    ticket,
                    csrfToken
            );
            
            if (response.getData() == null) {
                throw new RuntimeException("No task ID returned from Proxmox");
            }
            
            log.info("Snapshot rollback task started: {}", response.getData());
            return new TaskResponse(response.getData(), 
                    "Rollback to snapshot '" + snapshotName + "' started for VM " + vmId);
                    
        } catch (Exception e) {
            log.error("Failed to rollback snapshot for VM {}: {}", vmId, e.getMessage());
            throw new RuntimeException("Failed to rollback snapshot: " + e.getMessage(), e);
        }
    }
    
    private SnapshotResponse convertSnapshot(Snapshot snapshot) {
        return new SnapshotResponse(
                snapshot.getName(),
                snapshot.getDescription(),
                snapshot.getParent(),
                snapshot.getSnaptime(),
                snapshot.hasVmState(),
                snapshot.getSize()
        );
    }
    
    /**
     * Create snapshots for multiple VMs based on selectors
     */
    public BulkSnapshotResponse bulkCreateSnapshots(BulkSnapshotRequest request, @AuthTicket String ticket) {
        log.info("Starting bulk snapshot creation with {} selectors", request.vmSelectors().size());
        
        // Get all VMs that match the selectors
        List<VMResponse> targetVMs = new ArrayList<>();
        for (VMSelector selector : request.vmSelectors()) {
            try {
                List<VMResponse> vms = vmSelectorService.selectVMs(selector, ticket);
                targetVMs.addAll(vms);
            } catch (Exception e) {
                log.error("Failed to select VMs with selector {}: {}", selector, e.getMessage());
                throw new RuntimeException("Failed to select VMs: " + e.getMessage(), e);
            }
        }
        
        // Remove duplicates (in case multiple selectors matched the same VM)
        Map<Integer, VMResponse> uniqueVMs = new HashMap<>();
        for (VMResponse vm : targetVMs) {
            uniqueVMs.put(vm.vmid(), vm);
        }
        targetVMs = new ArrayList<>(uniqueVMs.values());
        
        log.info("Found {} unique VMs matching selectors", targetVMs.size());
        
        if (targetVMs.isEmpty()) {
            return new BulkSnapshotResponse(
                Map.of(),
                "No VMs found matching the provided selectors",
                0, 0, 0,
                request.dryRun()
            );
        }
        
        // Prepare results map
        Map<Integer, BulkSnapshotResponse.SnapshotResult> results = new HashMap<>();
        
        // If dry run, just show what would be done
        if (request.dryRun()) {
            for (VMResponse vm : targetVMs) {
                String snapshotName = expandSnapshotName(request.snapshotName(), vm);
                results.put(vm.vmid(), BulkSnapshotResponse.SnapshotResult.dryRun(snapshotName, vm.name()));
            }
            
            return new BulkSnapshotResponse(
                results,
                String.format("Dry run: Would create snapshots for %d VMs", targetVMs.size()),
                targetVMs.size(),
                targetVMs.size(),
                0,
                true
            );
        }
        
        // Create executor for parallel snapshot creation
        ExecutorService executor = Executors.newFixedThreadPool(request.maxParallel());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (VMResponse vm : targetVMs) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String snapshotName = expandSnapshotName(request.snapshotName(), vm);
                    
                    // Build snapshot request
                    CreateSnapshotRequest snapshotRequest = new CreateSnapshotRequest(
                        snapshotName,
                        request.description(),
                        request.includeMemory(),
                        request.ttlHours()
                    );
                    
                    // Create snapshot
                    TaskResponse task = createSnapshot(vm.vmid(), snapshotRequest, ticket);
                    results.put(vm.vmid(), BulkSnapshotResponse.SnapshotResult.success(
                        task.taskId(), snapshotName, vm.name()
                    ));
                    
                    log.info("Successfully created snapshot '{}' for VM {} ({})", 
                        snapshotName, vm.vmid(), vm.name());
                } catch (Exception e) {
                    log.error("Failed to create snapshot for VM {} ({}): {}", 
                        vm.vmid(), vm.name(), e.getMessage());
                    results.put(vm.vmid(), BulkSnapshotResponse.SnapshotResult.error(
                        e.getMessage(), vm.name()
                    ));
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error waiting for bulk snapshot completion: {}", e.getMessage());
            executor.shutdownNow();
        }
        
        // Count successes and failures
        long successCount = results.values().stream()
            .filter(r -> "success".equals(r.status()))
            .count();
        long failureCount = results.values().stream()
            .filter(r -> "error".equals(r.status()))
            .count();
        
        String summary = String.format("Created snapshots for %d/%d VMs successfully",
            successCount, targetVMs.size());
        
        return new BulkSnapshotResponse(
            results,
            summary,
            targetVMs.size(),
            (int) successCount,
            (int) failureCount,
            false
        );
    }
    
    private String expandSnapshotName(String pattern, VMResponse vm) {
        LocalDateTime now = LocalDateTime.now();
        String expanded = pattern
            .replace("{vm}", vm.name() != null ? vm.name() : String.valueOf(vm.vmid()))
            .replace("{date}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("{time}", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        
        // Ensure the name is valid (alphanumeric, dash, underscore)
        expanded = expanded.replaceAll("[^a-zA-Z0-9_-]", "-");
        
        // Ensure it's not too long
        if (expanded.length() > 60) {
            expanded = expanded.substring(0, 60);
        }
        
        return expanded;
    }
}