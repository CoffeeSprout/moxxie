# Moxxie UI Development Guide - Qute Templates & Java Services

This guide documents the Java services and data models available for server-side rendering with Qute templates.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Service Injection](#service-injection)
3. [Dashboard Services](#dashboard-services)
4. [VM Management Services](#vm-management-services)
5. [Snapshot Services](#snapshot-services)
6. [Backup Services](#backup-services)
7. [Scheduler Services](#scheduler-services)
8. [Storage Services](#storage-services)
9. [Network Services](#network-services)
10. [Node Services](#node-services)
11. [Migration Services](#migration-services)
12. [Tag Services](#tag-services)
13. [Console Services](#console-services)
14. [Audit Services](#audit-services)
15. [Cluster Provisioning Services](#cluster-provisioning-services)
16. [Common Patterns](#common-patterns)

## Architecture Overview

In Qute-based server-side rendering, your Resource classes will:
1. Inject the required services
2. Call service methods to get data
3. Pass data to Qute templates
4. Return rendered HTML

Example Resource:
```java
@Path("/ui/vms")
@ApplicationScoped
public class VMUIResource {
    
    @Inject
    VMService vmService;
    
    @Inject
    TagService tagService;
    
    @Inject
    Template vmList; // Qute template injection
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listVMs(@QueryParam("tags") String tags,
                                   @QueryParam("status") String status) {
        // Call services to get data
        List<VMResponse> vms = vmService.listVMsWithFilters(
            tags != null ? List.of(tags.split(",")) : null,
            null, // client
            null, // node
            status,
            null  // ticket handled by @AutoAuthenticate
        );
        
        List<TagSummary> availableTags = tagService.getAllTagsSummary(null);
        
        // Pass data to template
        return vmList.data("vms", vms)
                    .data("availableTags", availableTags)
                    .data("currentFilters", Map.of(
                        "tags", tags != null ? tags : "",
                        "status", status != null ? status : ""
                    ));
    }
}
```

## Service Injection

All services use CDI injection and most are annotated with `@AutoAuthenticate` for automatic Proxmox authentication:

```java
@Inject
VMService vmService;

@Inject
SnapshotService snapshotService;

@Inject
ClusterProvisioningService clusterService;
```

## Dashboard Services

### ClusterService
```java
@ApplicationScoped
@AutoAuthenticate
public class ClusterService {
    
    // Get cluster status overview
    public ClusterStatusResponse getClusterStatus(@AuthTicket String ticket);
    
    // Get all cluster resources (VMs, storage, nodes)
    public List<ClusterResourceResponse> getClusterResources(
        String type, // Optional: filter by "vm", "storage", "node"
        @AuthTicket String ticket
    );
}
```

### NodeService
```java
@ApplicationScoped
@AutoAuthenticate
public class NodeService {
    
    // Get all nodes with their status
    public List<NodeResponse> getAllNodes(@AuthTicket String ticket);
    
    // Get node names only
    public List<String> getNodeNames(@AuthTicket String ticket);
}
```

### TaskService
```java
@ApplicationScoped
@AutoAuthenticate
public class TaskService {
    
    // Get recent tasks with filtering
    public List<TaskResponse> getRecentTasks(
        String node,     // Optional
        Integer limit,   // Default: 50
        String userfilter,
        String typefilter,
        String statusfilter, // e.g., "running,error"
        @AuthTicket String ticket
    );
    
    // Get task status by UPID
    public TaskStatusResponse getTaskStatus(String upid, @AuthTicket String ticket);
}
```

### Data Models
```java
public record ClusterStatusResponse(
    ClusterInfo cluster,
    List<NodeStatus> nodes
) {}

public record ClusterInfo(
    String name,
    int version,
    int nodes,
    boolean quorate
) {}

public record NodeStatus(
    String name,
    String type,
    boolean online,
    String ip,
    boolean local
) {}
```

## VM Management Services

### VMService
```java
@ApplicationScoped
@AutoAuthenticate
public class VMService {
    
    // List all VMs
    public List<VMResponse> listVMs(@AuthTicket String ticket);
    
    // List VMs with filters
    public List<VMResponse> listVMsWithFilters(
        List<String> tags,
        String client,
        String node,
        String status,
        @AuthTicket String ticket
    );
    
    // Get single VM details
    public VMResponse getVM(String node, int vmId, @AuthTicket String ticket);
    
    // Get VM configuration
    public Map<String, Object> getVMConfig(String node, int vmId, @AuthTicket String ticket);
    
    // Create VM
    public CreateVMResponse createVM(String node, CreateVMRequest request, @AuthTicket String ticket);
    
    // Power operations
    public TaskResponse startVM(int vmId, @AuthTicket String ticket);
    public TaskResponse stopVM(int vmId, boolean force, @AuthTicket String ticket);
    public TaskResponse rebootVM(int vmId, @AuthTicket String ticket);
    public TaskResponse shutdownVM(int vmId, boolean forceStop, Integer timeout, @AuthTicket String ticket);
    
    // Delete VM
    public TaskResponse deleteVM(int vmId, boolean purge, @AuthTicket String ticket);
}
```

### VMLocatorService
```java
@ApplicationScoped
@AutoAuthenticate
public class VMLocatorService {
    
    // Find which node a VM is on
    public String findNodeForVM(int vmId, @AuthTicket String ticket);
    
    // Find VM across all nodes
    public VMResponse findVM(int vmId, @AuthTicket String ticket);
    
    // Find multiple VMs
    public Map<Integer, VMResponse> findVMs(List<Integer> vmIds, @AuthTicket String ticket);
}
```

### Data Models
```java
public record VMResponse(
    int vmid,
    String name,
    String node,
    String type,
    String status,
    double cpu,
    int cpus,
    long mem,
    long maxmem,
    long disk,
    long maxdisk,
    long netin,
    long netout,
    long diskread,
    long diskwrite,
    long uptime,
    boolean template,
    List<String> tags,
    String lock,
    String hastate
) {}

public record CreateVMRequest(
    String node,
    Integer vmId,
    String name,
    int cores,
    int memory,
    List<DiskConfig> disks,
    List<NetworkConfig> networks,
    String osType,
    String bootOrder,
    boolean start,
    List<String> tags
) {}
```

## Snapshot Services

### SnapshotService
```java
@ApplicationScoped
@AutoAuthenticate
public class SnapshotService {
    
    // List snapshots for a VM
    public List<SnapshotResponse> listSnapshots(int vmId, @AuthTicket String ticket);
    
    // Create snapshot
    public TaskResponse createSnapshot(
        int vmId,
        SnapshotRequest request,
        @AuthTicket String ticket
    );
    
    // Delete snapshot
    public TaskResponse deleteSnapshot(
        int vmId,
        String snapshotName,
        @AuthTicket String ticket
    );
    
    // Rollback to snapshot
    public TaskResponse rollbackSnapshot(
        int vmId,
        String snapshotName,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record SnapshotResponse(
    String name,
    Long snaptime,
    String description,
    String parent,
    boolean vmstate,
    boolean running
) {}

public record SnapshotRequest(
    String name,
    String description,
    boolean includeVMState,
    Integer ttlHours
) {}
```

## Backup Services

### BackupService
```java
@ApplicationScoped
@AutoAuthenticate
public class BackupService {
    
    // List all backups
    public List<BackupResponse> listBackups(
        String storage,
        Integer vmId,
        @AuthTicket String ticket
    );
    
    // Get backup configuration
    public Map<String, Object> getBackupConfig(
        String storage,
        String backupId,
        @AuthTicket String ticket
    );
    
    // Create backup
    public TaskResponse createBackup(
        int vmId,
        BackupRequest request,
        @AuthTicket String ticket
    );
    
    // Delete backup
    public TaskResponse deleteBackup(
        String storage,
        String backupId,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record BackupResponse(
    String volid,
    String content,
    long ctime,
    String format,
    long size,
    int vmid,
    String notes,
    boolean protected,
    BackupVerification verification
) {}

public record BackupRequest(
    String storage,
    String mode,
    String compress,
    String notes,
    boolean protected,
    Integer removeOlder
) {}
```

## Scheduler Services

### SchedulerService
```java
@ApplicationScoped
public class SchedulerService {
    
    // List all scheduled jobs
    public List<SchedulerJob> getAllJobs();
    
    // Get job by ID
    public SchedulerJob getJob(String jobId);
    
    // Create new job
    public SchedulerJob createJob(SchedulerJobRequest request);
    
    // Update job
    public SchedulerJob updateJob(String jobId, SchedulerJobRequest request);
    
    // Delete job
    public void deleteJob(String jobId);
    
    // Trigger job manually
    public SchedulerExecution triggerJob(String jobId);
    
    // Get job execution history
    public List<SchedulerExecution> getJobHistory(String jobId, int limit);
}
```

### Data Models
```java
public record SchedulerJob(
    String id,
    String name,
    String taskType,
    String cronExpression,
    boolean enabled,
    Instant createdAt,
    Instant lastRun,
    String lastRunStatus,
    Instant nextRun,
    Map<String, String> parameters,
    List<VMSelector> vmSelectors,
    JobStatistics statistics
) {}

public record SchedulerJobRequest(
    String name,
    String taskType,
    String cronExpression,
    boolean enabled,
    Map<String, String> parameters,
    List<VMSelector> vmSelectors
) {}

public record VMSelector(
    String type, // ALL, VMID, VMID_LIST, TAG, TAG_EXPRESSION, NAME_PATTERN
    String value
) {}
```

## Storage Services

### StorageService
```java
@ApplicationScoped
@AutoAuthenticate
public class StorageService {
    
    // List all storage
    public List<StorageResponse> listStorage(
        String node,
        String content,
        Boolean enabled,
        @AuthTicket String ticket
    );
    
    // Get storage details
    public StorageResponse getStorage(
        String storageId,
        @AuthTicket String ticket
    );
    
    // Check if storage exists on node
    public boolean storageExists(
        String storageId,
        String node,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record StorageResponse(
    String storage,
    String type,
    List<String> content,
    boolean active,
    boolean enabled,
    boolean shared,
    long total,
    long used,
    long available,
    double used_fraction,
    List<String> nodes,
    String path,
    String server,
    String export
) {}
```

## Network Services

### NetworkService
```java
@ApplicationScoped
@AutoAuthenticate
public class NetworkService {
    
    // List network interfaces
    public List<NetworkInterface> listNetworks(
        String node,
        String type,
        @AuthTicket String ticket
    );
    
    // Check if bridge exists
    public boolean bridgeExists(
        String bridge,
        String node,
        @AuthTicket String ticket
    );
}
```

### SDNService
```java
@ApplicationScoped
public class SDNService {
    
    // List VNets
    public List<VNetResponse> listVNets();
    
    // List Zones
    public List<ZoneResponse> listZones();
    
    // Get subnets for VNet
    public List<SubnetResponse> getVNetSubnets(String vnet);
}
```

### Data Models
```java
public record NetworkInterface(
    String iface,
    String type,
    boolean active,
    boolean autostart,
    String bridge_ports,
    String address,
    String netmask,
    String gateway,
    List<String> families,
    String method,
    String comments
) {}

public record VNetResponse(
    String vnet,
    String zone,
    String type,
    int tag,
    String alias,
    String ipam,
    String mac
) {}
```

## Node Services

### NodeService (Extended)
```java
@ApplicationScoped
@AutoAuthenticate
public class NodeService {
    
    // Get node details
    public NodeDetailResponse getNode(
        String nodeName,
        @AuthTicket String ticket
    );
    
    // Get node services
    public List<ServiceStatus> getNodeServices(
        String nodeName,
        @AuthTicket String ticket
    );
    
    // Execute command on node
    public String executeNodeCommand(
        String nodeName,
        String command,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record NodeDetailResponse(
    String node,
    String status,
    double cpu,
    int maxcpu,
    long mem,
    long maxmem,
    long disk,
    long maxdisk,
    long uptime,
    String pveversion,
    String kversion,
    double[] loadavg,
    CpuInfo cpuinfo,
    FileSystemInfo rootfs,
    SwapInfo swap
) {}

public record ServiceStatus(
    String name,
    String desc,
    String state
) {}
```

## Migration Services

### MigrationService
```java
@ApplicationScoped
@AutoAuthenticate
public class MigrationService {
    
    // Check migration feasibility
    public MigrationCheckResponse checkMigration(
        int vmId,
        String targetNode,
        @AuthTicket String ticket
    );
    
    // Start migration
    public TaskResponse migrateVM(
        int vmId,
        MigrationRequest request,
        @AuthTicket String ticket
    );
    
    // Get migration status
    public MigrationStatus getMigrationStatus(
        int vmId,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record MigrationCheckResponse(
    boolean allowed,
    boolean running,
    List<LocalDisk> localDisks,
    List<String> warnings,
    List<TargetStorage> targetStorages
) {}

public record MigrationRequest(
    String targetNode,
    boolean online,
    boolean withLocalDisks,
    Map<String, String> targetStorage,
    boolean force
) {}

public record MigrationStatus(
    boolean running,
    double progress,
    String status,
    Instant startTime,
    Instant estimatedCompletion,
    String speed,
    long downtime
) {}
```

## Tag Services

### TagService
```java
@ApplicationScoped
@AutoAuthenticate
public class TagService {
    
    // Get all tags with counts
    public List<TagSummary> getAllTagsSummary(@AuthTicket String ticket);
    
    // Get VMs by tag
    public List<VMResponse> getVMsByTag(String tag, @AuthTicket String ticket);
    
    // Add tags to VM
    public void addTagsToVM(int vmId, List<String> tags, @AuthTicket String ticket);
    
    // Remove tags from VM
    public void removeTagsFromVM(int vmId, List<String> tags, @AuthTicket String ticket);
    
    // Bulk tag operations
    public BulkTagResult performBulkTagOperation(
        BulkTagRequest request,
        String namePattern,
        List<Integer> vmIds,
        String node,
        String pool,
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record TagSummary(
    String name,
    int count,
    String category,
    String color
) {}

public record BulkTagRequest(
    BulkTagAction action, // ADD, REMOVE, REPLACE
    List<String> tags
) {}

public record BulkTagResult(
    int totalVMs,
    int modified,
    List<TagOperationResult> results
) {}
```

## Console Services

### ConsoleService
```java
@ApplicationScoped
@AutoAuthenticate
public class ConsoleService {
    
    // Get console access information
    public ConsoleAccessResponse getConsoleAccess(
        int vmId,
        ConsoleType type, // NOVNC, SPICE, XTERM
        @AuthTicket String ticket
    );
}
```

### Data Models
```java
public record ConsoleAccessResponse(
    String url,
    String ticket,
    int port,
    String protocol,
    String host,
    String password,
    String cert
) {}
```

## Audit Services

### AuditService
```java
@ApplicationScoped
public class AuditService {
    
    // Get audit log entries
    public List<AuditEntry> getAuditLog(
        Instant start,
        Instant end,
        String user,
        String operation,
        Integer limit,
        Integer offset
    );
    
    // Log an operation
    public void logOperation(
        String operation,
        String resourceType,
        String resourceId,
        Map<String, Object> details,
        boolean success,
        String error
    );
}
```

### Data Models
```java
public record AuditEntry(
    String id,
    Instant timestamp,
    String user,
    String sourceIp,
    String operation,
    String resourceType,
    String resourceId,
    String node,
    Map<String, Object> details,
    boolean success,
    String error,
    double duration,
    String task
) {}
```

## Cluster Provisioning Services

### ClusterProvisioningService
```java
@ApplicationScoped
public class ClusterProvisioningService {
    
    // Provision a new cluster
    public Uni<ClusterProvisioningResponse> provisionCluster(
        ClusterSpec spec,
        String baseUrl
    );
    
    // Get operation state
    public ClusterProvisioningState getOperationState(String operationId);
    
    // Get all operations
    public Collection<ClusterProvisioningState> getAllOperations();
    
    // Cancel operation
    public boolean cancelOperation(String operationId);
}
```

### Data Models
```java
public record ClusterSpec(
    String name,
    ClusterType type, // TALOS, K3S, GENERIC
    List<NodeGroupSpec> nodeGroups,
    NetworkTopology networkTopology,
    GlobalCloudInit globalCloudInit,
    Map<String, String> metadata,
    ProvisioningOptions options
) {}

public record NodeGroupSpec(
    String name,
    NodeRole role, // CONTROL_PLANE, WORKER, GENERIC
    int count,
    NodeTemplate template,
    PlacementConstraints placementConstraints,
    CloudInitConfig cloudInit
) {}

public record NodeTemplate(
    int cores,
    int memory,
    String diskSize,
    String storagePool,
    String imageSource,
    List<AdditionalDisk> additionalDisks,
    List<NetworkInterface> networkInterfaces
) {}

public record ClusterProvisioningResponse(
    String operationId,
    ProvisioningStatus status,
    String message,
    String clusterName,
    ClusterType clusterType,
    Instant startTime,
    Instant completionTime,
    Instant estimatedCompletion,
    int nodeCount,
    ProvisioningProgress progress,
    List<ProvisionedNode> nodes,
    ClusterAccess clusterAccess,
    String error,
    Map<String, String> links
) {}
```

## Common Patterns

### Template Data Preparation
```java
@Path("/ui/dashboard")
@ApplicationScoped
public class DashboardUIResource {
    
    @Inject
    ClusterService clusterService;
    
    @Inject
    VMService vmService;
    
    @Inject
    TaskService taskService;
    
    @Inject
    Template dashboard;
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showDashboard() {
        // Parallel data fetching for performance
        var clusterStatus = clusterService.getClusterStatus(null);
        var vmList = vmService.listVMs(null);
        var recentTasks = taskService.getRecentTasks(null, 10, null, null, "running,error", null);
        
        // Calculate summary statistics
        var vmStats = calculateVMStats(vmList);
        var resourceUsage = calculateResourceUsage(vmList, clusterStatus.nodes());
        
        return dashboard
            .data("cluster", clusterStatus)
            .data("vmStats", vmStats)
            .data("resourceUsage", resourceUsage)
            .data("recentTasks", recentTasks)
            .data("alerts", generateAlerts(clusterStatus, vmList));
    }
}
```

### Form Handling
```java
@POST
@Path("/create")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.TEXT_HTML)
public Response createVM(@BeanParam CreateVMForm form) {
    try {
        // Convert form to API request
        var request = buildCreateVMRequest(form);
        
        // Call service
        var response = vmService.createVM(form.node, request, null);
        
        // Redirect to VM detail page
        return Response.seeOther(URI.create("/ui/vms/" + response.vmId())).build();
        
    } catch (ValidationException e) {
        // Re-render form with errors
        return Response.ok(vmCreate
            .data("form", form)
            .data("errors", e.getErrors())
            .data("nodes", nodeService.getAllNodes(null))
            .data("storage", storageService.listStorage(null, "images", true, null))
        ).build();
    }
}
```

### Error Handling
```java
@Inject
Template errorPage;

@ServerExceptionMapper
public Response handleException(Exception e) {
    log.error("UI error", e);
    
    return Response.status(500)
        .entity(errorPage
            .data("error", e.getMessage())
            .data("type", e.getClass().getSimpleName())
            .data("timestamp", Instant.now())
        )
        .build();
}
```

### HTMX Integration
```java
@Path("/ui/vms/{vmId}/status")
@GET
@Produces(MediaType.TEXT_HTML)
public TemplateInstance getVMStatusFragment(@PathParam("vmId") int vmId) {
    var vm = vmLocatorService.findVM(vmId, null);
    
    // Return just the status fragment for HTMX update
    return vmStatusFragment.data("vm", vm);
}

@Path("/ui/tasks/{taskId}/progress")
@GET
@Produces(MediaType.TEXT_HTML)
public TemplateInstance getTaskProgress(@PathParam("taskId") String taskId) {
    var task = taskService.getTaskStatus(taskId, null);
    
    // Return progress bar fragment
    return taskProgressFragment
        .data("task", task)
        .data("complete", !"running".equals(task.status()));
}
```

### Pagination Support
```java
public class PaginationHelper {
    
    public static <T> PaginatedResult<T> paginate(
            List<T> items, 
            int page, 
            int pageSize) {
        
        int total = items.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int offset = (page - 1) * pageSize;
        
        List<T> pageItems = items.stream()
            .skip(offset)
            .limit(pageSize)
            .toList();
        
        return new PaginatedResult<>(
            pageItems,
            page,
            pageSize,
            total,
            totalPages,
            page > 1,
            page < totalPages
        );
    }
}
```

### Template Utilities
```java
@TemplateExtension
public class FormatExtensions {
    
    public static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    public static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format("%dm %ds", seconds / 60, seconds % 60);
        if (seconds < 86400) return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        return String.format("%dd %dh", seconds / 86400, (seconds % 86400) / 3600);
    }
    
    public static String percentage(double value) {
        return String.format("%.1f%%", value * 100);
    }
}
```

## Notes for Qute Development

1. **Authentication**: Services marked with `@AutoAuthenticate` handle authentication automatically - pass `null` for the ticket parameter

2. **Async Operations**: Many operations return `TaskResponse` - you'll need to poll or use HTMX to check progress

3. **Error Handling**: Services throw `ProxmoxException` for API errors - catch and display user-friendly messages

4. **Performance**: Consider caching frequently accessed data like node lists, storage configurations

5. **HTMX Patterns**: Use fragments for dynamic updates, return minimal HTML for efficiency

6. **Form Validation**: Use Bean Validation annotations on form classes, re-render with errors

7. **Template Organization**: Use template fragments for reusable components (status badges, resource bars, etc.)