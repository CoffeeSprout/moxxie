package com.coffeesprout.service;

import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.TaskResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.Snapshot;
import com.coffeesprout.client.SnapshotsResponse;
import com.coffeesprout.client.TaskStatusResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
}