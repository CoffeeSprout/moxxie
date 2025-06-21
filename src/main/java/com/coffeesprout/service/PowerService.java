package com.coffeesprout.service;

import com.coffeesprout.api.dto.BulkPowerRequest;
import com.coffeesprout.api.dto.BulkPowerResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.scheduler.service.VMSelectorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@AutoAuthenticate
public class PowerService {
    
    private static final Logger log = LoggerFactory.getLogger(PowerService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    VMSelectorService vmSelectorService;
    
    /**
     * Perform bulk power operation on multiple VMs
     */
    public BulkPowerResponse bulkPowerOperation(BulkPowerRequest request, @AuthTicket String ticket) {
        log.info("Starting bulk {} operation with {} selectors", 
                request.operation(), request.vmSelectors().size());
        
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
            return new BulkPowerResponse(
                Map.of(),
                "No VMs found matching the provided selectors",
                0, 0, 0, 0,
                request.dryRun()
            );
        }
        
        // Prepare results map - use concurrent map for thread safety
        Map<Integer, BulkPowerResponse.PowerResult> results = new ConcurrentHashMap<>();
        
        // If dry run, just show what would be done
        if (request.dryRun()) {
            for (VMResponse vm : targetVMs) {
                String targetState = getTargetState(request.operation());
                results.put(vm.vmid(), BulkPowerResponse.PowerResult.dryRun(
                    vm.status(), targetState, vm.name()
                ));
            }
            
            return new BulkPowerResponse(
                results,
                String.format("Dry run: Would perform %s on %d VMs", 
                    request.operation(), targetVMs.size()),
                targetVMs.size(),
                targetVMs.size(),
                0,
                0,
                true
            );
        }
        
        // Create executor for parallel operations
        ExecutorService executor = Executors.newFixedThreadPool(request.maxParallel());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (VMResponse vm : targetVMs) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Check if we should skip this VM
                    if (request.skipIfAlreadyInState() && isInTargetState(vm, request.operation())) {
                        results.put(vm.vmid(), BulkPowerResponse.PowerResult.skipped(
                            vm.status(), "Already in desired state", vm.name()
                        ));
                        log.info("Skipping VM {} ({}) - already {}", 
                            vm.vmid(), vm.name(), vm.status());
                        return;
                    }
                    
                    // Find the node for this VM
                    String node = vm.node();
                    String previousState = vm.status();
                    
                    // Perform the operation
                    log.info("Performing {} on VM {} ({}) on node {}", 
                        request.operation(), vm.vmid(), vm.name(), node);
                    
                    switch (request.operation()) {
                        case START:
                            vmService.startVM(node, vm.vmid(), ticket);
                            break;
                        case STOP:
                            vmService.stopVM(node, vm.vmid(), ticket);
                            break;
                        case SHUTDOWN:
                            vmService.shutdownVM(node, vm.vmid(), ticket);
                            break;
                        case REBOOT:
                            vmService.rebootVM(node, vm.vmid(), ticket);
                            break;
                        case SUSPEND:
                            // TODO: Implement suspend when available
                            throw new UnsupportedOperationException("Suspend not yet implemented");
                        case RESUME:
                            // TODO: Implement resume when available
                            throw new UnsupportedOperationException("Resume not yet implemented");
                    }
                    
                    // For now, we don't have task IDs from these operations
                    // In a real implementation, we'd capture the task ID
                    results.put(vm.vmid(), BulkPowerResponse.PowerResult.success(
                        previousState, getTargetState(request.operation()), 
                        "task-" + vm.vmid(), vm.name()
                    ));
                    
                    log.info("Successfully performed {} on VM {} ({})", 
                        request.operation(), vm.vmid(), vm.name());
                    
                } catch (Exception e) {
                    log.error("Failed to perform {} on VM {} ({}): {}", 
                        request.operation(), vm.vmid(), vm.name(), e.getMessage());
                    results.put(vm.vmid(), BulkPowerResponse.PowerResult.error(
                        vm.status(), e.getMessage(), vm.name()
                    ));
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            executor.shutdown();
            executor.awaitTermination(request.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error waiting for bulk power operation completion: {}", e.getMessage());
            executor.shutdownNow();
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
        
        String summary = String.format("Performed %s on %d/%d VMs successfully",
            request.operation(), successCount, targetVMs.size());
        if (skippedCount > 0) {
            summary += String.format(" (%d skipped)", skippedCount);
        }
        if (failureCount > 0) {
            summary += String.format(" (%d failed)", failureCount);
        }
        
        return new BulkPowerResponse(
            results,
            summary,
            targetVMs.size(),
            (int) successCount,
            (int) failureCount,
            (int) skippedCount,
            false
        );
    }
    
    private boolean isInTargetState(VMResponse vm, BulkPowerRequest.PowerOperation operation) {
        String status = vm.status();
        return switch (operation) {
            case START, RESUME -> "running".equals(status);
            case STOP, SHUTDOWN -> "stopped".equals(status);
            case SUSPEND -> "suspended".equals(status);
            case REBOOT -> false; // Reboot always executes
        };
    }
    
    private String getTargetState(BulkPowerRequest.PowerOperation operation) {
        return switch (operation) {
            case START, RESUME, REBOOT -> "running";
            case STOP, SHUTDOWN -> "stopped";
            case SUSPEND -> "suspended";
        };
    }
}