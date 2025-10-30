package com.coffeesprout.service;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.DiskInfo;
import com.coffeesprout.api.dto.PoolResourceSummary;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class PoolService {

    private static final Logger LOG = LoggerFactory.getLogger(PoolService.class);

    @Inject
    VMService vmService;

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @SafeMode(false)  // Read operation
    public List<PoolResourceSummary> getPoolResourceSummaries(@AuthTicket String ticket) {
        try {
            // Get all pools from Proxmox
            PoolsResponse poolsResponse = proxmoxClient.listPools(ticket);
            List<Pool> pools = poolsResponse.getData();

            // Get all VMs for quick lookup
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Map<Integer, VMResponse> vmMap = allVMs.stream()
                .collect(Collectors.toMap(VMResponse::vmid, vm -> vm, (v1, v2) -> v1));

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
                    List<VMResponse> poolVMs = poolDetail.getData().getMembers().stream()
                        .filter(member -> "qemu".equals(member.getType()))
                        .map(member -> vmMap.get(member.getVmid()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    if (!poolVMs.isEmpty()) {
                        PoolResourceSummary summary = createPoolSummary(pool.getPoolid(), poolVMs, ticket);
                        summaries.add(summary);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to get details for pool: " + pool.getPoolid(), e);
                }
            }

            // Sort by pool name
            summaries.sort(Comparator.comparing(PoolResourceSummary::poolName));

            return summaries;
        } catch (Exception e) {
            LOG.error("Failed to get pool resource summaries", e);
            throw ProxmoxException.internalError("get pool resource summaries", e);
        }
    }

    @SafeMode(false)  // Read operation
    public PoolResourceSummary getPoolResourceSummary(String poolName, @AuthTicket String ticket) {
        try {
            // Get pool details from Proxmox
            PoolDetailResponse poolDetail = proxmoxClient.getPool(poolName, ticket);
            if (poolDetail.getData() == null || poolDetail.getData().getMembers() == null) {
                throw ProxmoxException.notFound("Pool", poolName,
                    "Check pool name or verify pool has members");
            }

            // Get all VMs for quick lookup
            List<VMResponse> allVMs = vmService.listVMs(ticket);
            Map<Integer, VMResponse> vmMap = allVMs.stream()
                .collect(Collectors.toMap(VMResponse::vmid, vm -> vm, (v1, v2) -> v1));

            // Filter VMs from pool members
            List<VMResponse> poolVMs = poolDetail.getData().getMembers().stream()
                .filter(member -> "qemu".equals(member.getType()))
                .map(member -> vmMap.get(member.getVmid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (poolVMs.isEmpty()) {
                throw ProxmoxException.notFound("VMs in pool", poolName,
                    "Pool exists but contains no VMs or only non-QEMU resources");
            }

            return createPoolSummary(poolName, poolVMs, ticket);
        } catch (ProxmoxException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get pool resource summary for: " + poolName, e);
            throw ProxmoxException.internalError("get pool resource summary for " + poolName, e);
        }
    }

    private Map<String, List<VMResponse>> groupVMsByPool(List<VMResponse> vms) {
        Map<String, List<VMResponse>> vmsByPool = new HashMap<>();

        for (VMResponse vm : vms) {
            String poolName = getPoolName(vm);
            vmsByPool.computeIfAbsent(poolName, k -> new ArrayList<>()).add(vm);
        }

        return vmsByPool;
    }

    private String getPoolName(VMResponse vm) {
        if (vm.name() == null || vm.name().isEmpty()) {
            return "unnamed";
        }

        // Extract pool name from VM name (e.g., "nixz-web-01" -> "nixz")
        String vmName = vm.name();
        int dashIndex = vmName.indexOf('-');
        if (dashIndex > 0) {
            return vmName.substring(0, dashIndex);
        }

        // If no dash, use the whole name as pool name
        return vmName;
    }

    private PoolResourceSummary createPoolSummary(String poolName, List<VMResponse> vms, String ticket) {
        long totalMemory = 0;
        int totalVcpus = 0;
        long totalStorage = 0;

        int runningVMs = 0;
        int stoppedVMs = 0;
        long runningMemory = 0;
        int runningVcpus = 0;

        List<PoolResourceSummary.VMSummary> vmSummaries = new ArrayList<>();

        for (VMResponse vm : vms) {
            // Get VM storage from configuration
            long vmStorage = calculateVMTotalStorage(vm, ticket);

            totalMemory += vm.maxmem();
            totalVcpus += vm.cpus();
            totalStorage += vmStorage;

            // Count running vs stopped VMs
            if ("running".equals(vm.status())) {
                runningVMs++;
                runningMemory += vm.maxmem();
                runningVcpus += vm.cpus();
            } else {
                stoppedVMs++;
            }

            PoolResourceSummary.VMSummary vmSummary = new PoolResourceSummary.VMSummary(
                vm.vmid(),
                vm.name() != null ? vm.name() : "VM-" + vm.vmid(),
                vm.cpus(),
                vm.maxmem(),
                vmStorage,
                vm.status(),
                vm.node()
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

    private long calculateVMTotalStorage(VMResponse vm, String ticket) {
        try {
            // Skip invalid VMs
            if (vm.vmid() <= 0 || vm.node() == null || vm.node().isEmpty()) {
                return 0;
            }

            // Get VM configuration
            VMConfigResponse response = proxmoxClient.getVMConfig(vm.node(), vm.vmid(), ticket);
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
            LOG.warn("Failed to get storage info for VM {}: {}", vm.vmid(), e.getMessage());
            return vm.maxdisk(); // Fallback to maxdisk if config fetch fails
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
            LOG.warn("Failed to parse disk info for {}: {}", diskInterface, diskSpec, e);
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
            LOG.warn("Failed to parse disk size: {}", sizeStr, e);
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
