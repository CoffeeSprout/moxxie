package com.coffeesprout.service;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.constants.VMConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper service for common resource operations.
 * Centralizes frequently used patterns to reduce code duplication across Resource classes.
 */
@ApplicationScoped
@AutoAuthenticate
public class ResourceHelperService {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceHelperService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    TicketManager ticketManager;
    
    /**
     * Find a VM by ID or throw an exception with context.
     * This pattern is used across multiple Resource classes.
     * 
     * @param vmId The VM ID to find
     * @param ticket Authentication ticket (auto-injected if null)
     * @return The VM response
     * @throws ProxmoxException if VM not found
     */
    public VMResponse findVMByIdOrThrow(int vmId, @AuthTicket String ticket) {
        log.debug("Looking up VM with ID: {}", vmId);
        
        List<VMResponse> vms = vmService.listVMs(ticket);
        
        return vms.stream()
            .filter(v -> v.vmid() == vmId)
            .findFirst()
            .orElseThrow(() -> {
                log.error("VM {} not found in cluster", vmId);
                return ProxmoxException.notFound("VM", String.valueOf(vmId),
                    "VM with ID " + vmId + " does not exist in the cluster. " +
                    "Available VM IDs: " + getAvailableVMIds(vms, 10));
            });
    }
    
    /**
     * Find a VM by ID, returning Optional.
     * 
     * @param vmId The VM ID to find
     * @param ticket Authentication ticket (auto-injected if null)
     * @return Optional containing the VM if found
     */
    public Optional<VMResponse> findVMById(int vmId, @AuthTicket String ticket) {
        log.debug("Looking up VM with ID: {}", vmId);
        
        List<VMResponse> vms = vmService.listVMs(ticket);
        
        return vms.stream()
            .filter(v -> v.vmid() == vmId)
            .findFirst();
    }
    
    /**
     * Find multiple VMs by their IDs.
     * 
     * @param vmIds List of VM IDs to find
     * @param ticket Authentication ticket (auto-injected if null)
     * @return List of found VMs (may be smaller than input if some VMs don't exist)
     */
    public List<VMResponse> findVMsByIds(List<Integer> vmIds, @AuthTicket String ticket) {
        log.debug("Looking up {} VMs", vmIds.size());
        
        List<VMResponse> allVms = vmService.listVMs(ticket);
        
        return allVms.stream()
            .filter(vm -> vmIds.contains(vm.vmid()))
            .collect(Collectors.toList());
    }
    
    /**
     * Validate that a VM is in the expected status for an operation.
     * 
     * @param vm The VM to check
     * @param expectedStatus The expected status
     * @param operation The operation being attempted (for error message)
     * @throws ProxmoxException if VM is not in expected status
     */
    public void validateVMStatus(VMResponse vm, String expectedStatus, String operation) {
        if (!expectedStatus.equals(vm.status())) {
            log.warn("VM {} status validation failed: expected '{}' but was '{}' for operation '{}'",
                vm.vmid(), expectedStatus, vm.status(), operation);
            
            throw ProxmoxException.conflict("VM",
                String.format("Cannot %s VM %d (%s): VM must be %s but is currently %s. " +
                    "Please %s the VM first.",
                    operation, vm.vmid(), vm.name(), expectedStatus, vm.status(),
                    getStatusChangeHint(vm.status(), expectedStatus)));
        }
    }
    
    /**
     * Validate that a VM is in one of the allowed statuses for an operation.
     * 
     * @param vm The VM to check
     * @param allowedStatuses List of allowed statuses
     * @param operation The operation being attempted
     * @throws ProxmoxException if VM is not in an allowed status
     */
    public void validateVMStatusIn(VMResponse vm, List<String> allowedStatuses, String operation) {
        if (!allowedStatuses.contains(vm.status())) {
            log.warn("VM {} status validation failed: status '{}' not in allowed list {} for operation '{}'",
                vm.vmid(), vm.status(), allowedStatuses, operation);
            
            throw ProxmoxException.conflict("VM",
                String.format("Cannot %s VM %d (%s): VM must be in one of %s but is currently %s",
                    operation, vm.vmid(), vm.name(), allowedStatuses, vm.status()));
        }
    }
    
    /**
     * Check if a VM is locked (e.g., during backup, migration, etc.).
     * 
     * @param vm The VM to check
     * @return true if VM is locked
     */
    public boolean isVMLocked(VMResponse vm) {
        // Check if VM has a lock field set
        // This would need to be added to VMResponse if not present
        return false; // Placeholder - actual implementation depends on VMResponse structure
    }
    
    /**
     * Validate that a VM is not locked before performing an operation.
     * 
     * @param vm The VM to check
     * @param operation The operation being attempted
     * @throws ProxmoxException if VM is locked
     */
    public void validateVMNotLocked(VMResponse vm, String operation) {
        if (isVMLocked(vm)) {
            log.warn("VM {} is locked, cannot perform operation '{}'", vm.vmid(), operation);
            
            throw ProxmoxException.conflict("VM",
                String.format("Cannot %s VM %d (%s): VM is currently locked by another operation. " +
                    "Please wait for the current operation to complete.",
                    operation, vm.vmid(), vm.name()));
        }
    }
    
    /**
     * Check if a VM is managed by Moxxie (has the moxxie tag).
     * 
     * @param vm The VM to check
     * @return true if VM is managed by Moxxie
     */
    public boolean isMoxxieManaged(VMResponse vm) {
        List<String> vmTags = vm.tags();
        if (vmTags == null || vmTags.isEmpty()) {
            return false;
        }
        
        for (String tag : vmTags) {
            if (VMConstants.Tags.MOXXIE_TAG.equals(tag.trim())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Validate that a VM is managed by Moxxie (for safe mode operations).
     * 
     * @param vm The VM to check
     * @param operation The operation being attempted
     * @throws ProxmoxException if VM is not Moxxie-managed
     */
    public void validateMoxxieManaged(VMResponse vm, String operation) {
        if (!isMoxxieManaged(vm)) {
            log.warn("VM {} is not Moxxie-managed, cannot perform operation '{}' in safe mode",
                vm.vmid(), operation);
            
            throw ProxmoxException.forbidden("VM",
                String.format("Cannot %s VM %d (%s): VM is not managed by Moxxie. " +
                    "Safe mode is enabled and only allows operations on Moxxie-managed VMs. " +
                    "Add the '%s' tag to this VM or disable safe mode.",
                    operation, vm.vmid(), vm.name(), VMConstants.Tags.MOXXIE_TAG));
        }
    }
    
    /**
     * Get a hint for changing VM status.
     * 
     * @param currentStatus Current VM status
     * @param targetStatus Desired VM status
     * @return Hint text for status change
     */
    private String getStatusChangeHint(String currentStatus, String targetStatus) {
        if (VMConstants.Status.STOPPED.equals(targetStatus) && VMConstants.Status.RUNNING.equals(currentStatus)) {
            return "stop";
        } else if (VMConstants.Status.RUNNING.equals(targetStatus) && VMConstants.Status.STOPPED.equals(currentStatus)) {
            return "start";
        } else if (VMConstants.Status.SUSPENDED.equals(targetStatus)) {
            return "suspend";
        } else if (VMConstants.Status.PAUSED.equals(targetStatus)) {
            return "pause";
        } else {
            return "change the status to " + targetStatus;
        }
    }
    
    /**
     * Get a sample of available VM IDs for error messages.
     * 
     * @param vms List of all VMs
     * @param limit Maximum number of IDs to return
     * @return String with sample VM IDs
     */
    private String getAvailableVMIds(List<VMResponse> vms, int limit) {
        if (vms.isEmpty()) {
            return "[no VMs found]";
        }
        
        String ids = vms.stream()
            .limit(limit)
            .map(vm -> String.valueOf(vm.vmid()))
            .collect(Collectors.joining(", "));
        
        if (vms.size() > limit) {
            ids += ", ... (" + (vms.size() - limit) + " more)";
        }
        
        return "[" + ids + "]";
    }
    
    /**
     * Find VMs by name pattern.
     * 
     * @param namePattern Pattern to match (supports wildcards)
     * @param ticket Authentication ticket
     * @return List of matching VMs
     */
    public List<VMResponse> findVMsByNamePattern(String namePattern, @AuthTicket String ticket) {
        log.debug("Looking up VMs with name pattern: {}", namePattern);
        
        List<VMResponse> allVms = vmService.listVMs(ticket);
        String regex = namePattern.replace("*", ".*");
        
        return allVms.stream()
            .filter(vm -> vm.name() != null && vm.name().matches(regex))
            .collect(Collectors.toList());
    }
    
    /**
     * Find VMs on a specific node.
     * 
     * @param node Node name
     * @param ticket Authentication ticket
     * @return List of VMs on the node
     */
    public List<VMResponse> findVMsByNode(String node, @AuthTicket String ticket) {
        log.debug("Looking up VMs on node: {}", node);
        
        List<VMResponse> allVms = vmService.listVMs(ticket);
        
        return allVms.stream()
            .filter(vm -> node.equals(vm.node()))
            .collect(Collectors.toList());
    }
    
    /**
     * Validate VM ID is within allowed range.
     * 
     * @param vmId VM ID to validate
     * @throws IllegalArgumentException if VM ID is out of range
     */
    public void validateVMId(int vmId) {
        if (vmId < VMConstants.Resources.MIN_VM_ID || vmId > VMConstants.Resources.MAX_VM_ID) {
            throw new IllegalArgumentException(
                String.format("VM ID %d is out of valid range (%d-%d)",
                    vmId, VMConstants.Resources.MIN_VM_ID, VMConstants.Resources.MAX_VM_ID));
        }
    }
    
    /**
     * Check if a VM name is already in use.
     * 
     * @param name VM name to check
     * @param ticket Authentication ticket
     * @return true if name is already used
     */
    public boolean isVMNameInUse(String name, @AuthTicket String ticket) {
        List<VMResponse> allVms = vmService.listVMs(ticket);
        
        return allVms.stream()
            .anyMatch(vm -> name.equals(vm.name()));
    }
    
    /**
     * Suggest an available VM ID.
     * 
     * @param startFrom Start searching from this ID
     * @param ticket Authentication ticket
     * @return Next available VM ID
     */
    public int suggestNextVMId(int startFrom, @AuthTicket String ticket) {
        List<VMResponse> allVms = vmService.listVMs(ticket);
        
        List<Integer> usedIds = allVms.stream()
            .map(VMResponse::vmid)
            .sorted()
            .collect(Collectors.toList());
        
        int nextId = Math.max(startFrom, VMConstants.Resources.MIN_VM_ID);
        
        while (usedIds.contains(nextId) && nextId <= VMConstants.Resources.MAX_VM_ID) {
            nextId++;
        }
        
        if (nextId > VMConstants.Resources.MAX_VM_ID) {
            throw new RuntimeException("No available VM IDs in range");
        }
        
        return nextId;
    }
}