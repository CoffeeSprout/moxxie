package com.coffeesprout.service;

import com.coffeesprout.api.dto.DiskInfo;
import com.coffeesprout.api.dto.PoolResourceSummary;
import com.coffeesprout.client.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class PoolService {
    
    private static final Logger log = LoggerFactory.getLogger(PoolService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @SafeMode(value = false)  // Read operation
    public List<PoolResourceSummary> getPoolResourceSummaries(String ticket) {
        try {
            // Get all pools from Proxmox
            PoolsResponse poolsResponse = proxmoxClient.listPools(ticket);
            List<Pool> pools = poolsResponse.getData();
            
            // Get all VMs for quick lookup
            List<VM> allVMs = vmService.listVMs(ticket);
            Map<Integer, VM> vmMap = allVMs.stream()
                .collect(Collectors.toMap(VM::getVmid, vm -> vm, (v1, v2) -> v1));
            
            // Create summaries for each pool
            List<PoolResourceSummary> summaries = new ArrayList<>();
            for (Pool pool : pools) {
                try {
                    // Get pool members
                    PoolDetailResponse poolDetail = proxmoxClient.getPool(pool.getPoolid(), ticket);
                    if (poolDetail.getData() == null || poolDetail.getData().getMembers() == null) {
                        continue;
                    }
                    
                    // Filter VMs from pool members
                    List<VM> poolVMs = poolDetail.getData().getMembers().stream()
                        .filter(member -> "qemu".equals(member.getType()))
                        .map(member -> vmMap.get(member.getVmid()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                    
                    if (!poolVMs.isEmpty()) {
                        PoolResourceSummary summary = createPoolSummary(pool.getPoolid(), poolVMs, ticket);
                        summaries.add(summary);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get details for pool: " + pool.getPoolid(), e);
                }
            }
            
            // Sort by pool name
            summaries.sort(Comparator.comparing(PoolResourceSummary::poolName));
            
            return summaries;
        } catch (Exception e) {
            log.error("Failed to get pool resource summaries", e);
            throw new RuntimeException("Failed to get pool resource summaries: " + e.getMessage(), e);
        }
    }
    
    @SafeMode(value = false)  // Read operation
    public PoolResourceSummary getPoolResourceSummary(String poolName, String ticket) {
        try {
            // Get pool details from Proxmox
            PoolDetailResponse poolDetail = proxmoxClient.getPool(poolName, ticket);
            if (poolDetail.getData() == null || poolDetail.getData().getMembers() == null) {
                throw new RuntimeException("Pool not found or has no members: " + poolName);
            }
            
            // Get all VMs for quick lookup
            List<VM> allVMs = vmService.listVMs(ticket);
            Map<Integer, VM> vmMap = allVMs.stream()
                .collect(Collectors.toMap(VM::getVmid, vm -> vm, (v1, v2) -> v1));
            
            // Filter VMs from pool members
            List<VM> poolVMs = poolDetail.getData().getMembers().stream()
                .filter(member -> "qemu".equals(member.getType()))
                .map(member -> vmMap.get(member.getVmid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (poolVMs.isEmpty()) {
                throw new RuntimeException("Pool has no VMs: " + poolName);
            }
            
            return createPoolSummary(poolName, poolVMs, ticket);
        } catch (Exception e) {
            log.error("Failed to get pool resource summary for: " + poolName, e);
            throw new RuntimeException("Failed to get pool resource summary: " + e.getMessage(), e);
        }
    }
    
    private Map<String, List<VM>> groupVMsByPool(List<VM> vms) {
        Map<String, List<VM>> vmsByPool = new HashMap<>();
        
        for (VM vm : vms) {
            String poolName = getPoolName(vm);
            vmsByPool.computeIfAbsent(poolName, k -> new ArrayList<>()).add(vm);
        }
        
        return vmsByPool;
    }
    
    private String getPoolName(VM vm) {
        if (vm.getName() == null || vm.getName().isEmpty()) {
            return "unnamed";
        }
        
        // Extract pool name from VM name (e.g., "nixz-web-01" -> "nixz")
        String vmName = vm.getName();
        int dashIndex = vmName.indexOf('-');
        if (dashIndex > 0) {
            return vmName.substring(0, dashIndex);
        }
        
        // If no dash, use the whole name as pool name
        return vmName;
    }
    
    private PoolResourceSummary createPoolSummary(String poolName, List<VM> vms, String ticket) {
        long totalMemory = 0;
        int totalVcpus = 0;
        long totalStorage = 0;
        
        int runningVMs = 0;
        int stoppedVMs = 0;
        long runningMemory = 0;
        int runningVcpus = 0;
        
        List<PoolResourceSummary.VMSummary> vmSummaries = new ArrayList<>();
        
        for (VM vm : vms) {
            // Get VM storage from configuration
            long vmStorage = calculateVMTotalStorage(vm, ticket);
            
            totalMemory += vm.getMaxmem();
            totalVcpus += vm.getCpus();
            totalStorage += vmStorage;
            
            // Count running vs stopped VMs
            if ("running".equals(vm.getStatus())) {
                runningVMs++;
                runningMemory += vm.getMaxmem();
                runningVcpus += vm.getCpus();
            } else {
                stoppedVMs++;
            }
            
            PoolResourceSummary.VMSummary vmSummary = new PoolResourceSummary.VMSummary(
                vm.getVmid(),
                vm.getName() != null ? vm.getName() : "VM-" + vm.getVmid(),
                vm.getCpus(),
                vm.getMaxmem(),
                vmStorage,
                vm.getStatus(),
                vm.getNode()
            );
            vmSummaries.add(vmSummary);
        }
        
        // Sort VMs by name
        vmSummaries.sort(Comparator.comparing(PoolResourceSummary.VMSummary::name));
        
        return new PoolResourceSummary(
            poolName,
            vms.size(),
            runningVMs,
            stoppedVMs,
            totalVcpus,
            runningVcpus,
            totalMemory,
            formatBytes(totalMemory),
            runningMemory,
            formatBytes(runningMemory),
            totalStorage,
            formatBytes(totalStorage),
            vmSummaries
        );
    }
    
    private long calculateVMTotalStorage(VM vm, String ticket) {
        try {
            // Skip invalid VMs
            if (vm.getVmid() <= 0 || vm.getNode() == null || vm.getNode().isEmpty()) {
                return 0;
            }
            
            // Get VM configuration
            VMConfigResponse response = proxmoxClient.getVMConfig(vm.getNode(), vm.getVmid(), ticket);
            Map<String, Object> config = response.getData();
            if (config == null) {
                return 0;
            }
            
            // Parse disk information
            List<DiskInfo> disks = parseDiskInfo(config);
            return disks.stream()
                .mapToLong(disk -> disk.sizeBytes() != null ? disk.sizeBytes() : 0L)
                .sum();
        } catch (Exception e) {
            log.warn("Failed to get storage info for VM {}: {}", vm.getVmid(), e.getMessage());
            return vm.getMaxdisk(); // Fallback to maxdisk if config fetch fails
        }
    }
    
    private List<DiskInfo> parseDiskInfo(Map<String, Object> config) {
        List<DiskInfo> disks = new ArrayList<>();
        String[] diskPrefixes = {"scsi", "virtio", "ide", "sata"};
        
        for (String prefix : diskPrefixes) {
            for (int i = 0; i < 30; i++) {
                String key = prefix + i;
                if (config.containsKey(key)) {
                    String diskSpec = config.get(key).toString();
                    if (diskSpec.contains("media=cdrom") || diskSpec.contains("cloudinit")) {
                        continue;
                    }
                    
                    DiskInfo diskInfo = parseSingleDisk(key, diskSpec);
                    if (diskInfo != null) {
                        disks.add(diskInfo);
                    }
                }
            }
        }
        
        return disks;
    }
    
    private DiskInfo parseSingleDisk(String diskInterface, String diskSpec) {
        try {
            String[] parts = diskSpec.split(",");
            if (parts.length == 0) return null;
            
            String[] storageParts = parts[0].split(":");
            if (storageParts.length < 2) return null;
            
            String storage = storageParts[0];
            String format = null;
            String sizeStr = null;
            Long sizeBytes = null;
            
            for (String part : parts) {
                if (part.startsWith("format=")) {
                    format = part.substring(7);
                } else if (part.startsWith("size=")) {
                    sizeStr = part.substring(5);
                    sizeBytes = parseDiskSize(sizeStr);
                }
            }
            
            if (sizeStr == null) {
                sizeStr = "unknown";
            }
            
            return new DiskInfo(
                diskInterface,
                storage,
                sizeBytes,
                sizeStr,
                format != null ? format : "raw",
                diskSpec
            );
        } catch (Exception e) {
            log.warn("Failed to parse disk info for {}: {}", diskInterface, diskSpec, e);
            return null;
        }
    }
    
    private Long parseDiskSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) return null;
        
        try {
            char unit = sizeStr.charAt(sizeStr.length() - 1);
            if (Character.isLetter(unit)) {
                long value = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 1));
                switch (Character.toUpperCase(unit)) {
                    case 'K': return value * 1024L;
                    case 'M': return value * 1024L * 1024L;
                    case 'G': return value * 1024L * 1024L * 1024L;
                    case 'T': return value * 1024L * 1024L * 1024L * 1024L;
                    default: return value;
                }
            } else {
                return Long.parseLong(sizeStr);
            }
        } catch (Exception e) {
            log.warn("Failed to parse disk size: {}", sizeStr, e);
            return null;
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}