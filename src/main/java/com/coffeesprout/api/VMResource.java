package com.coffeesprout.api;

import com.coffeesprout.api.dto.BackupRequest;
import com.coffeesprout.api.dto.BackupResponse;
import com.coffeesprout.api.dto.CloudInitVMRequest;
import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.CreateVMRequestDTO;
import com.coffeesprout.api.dto.DiskConfig;
import com.coffeesprout.api.dto.DiskInfo;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.api.dto.RestoreRequest;
import com.coffeesprout.api.dto.SetSSHKeysRequest;
import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.TaskResponse;
import com.coffeesprout.api.dto.VMDetailResponse;
import com.coffeesprout.api.dto.VMStatusDetailResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.CreateVMRequest;
import com.coffeesprout.client.CreateVMResponse;
import com.coffeesprout.client.VM;
import com.coffeesprout.client.VMStatusResponse;
import com.coffeesprout.client.TaskStatusResponse;
import com.coffeesprout.service.BackupService;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.SDNService;
import com.coffeesprout.service.SnapshotService;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import com.coffeesprout.service.VMIdService;
import com.coffeesprout.service.TicketManager;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.config.MoxxieConfig;
import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Path("/api/v1/vms")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "VMs", description = "Virtual Machine management endpoints")
public class VMResource {

    private static final Logger log = LoggerFactory.getLogger(VMResource.class);

    @Inject
    VMService vmService;
    
    @Inject
    TagService tagService;
    
    @Inject
    SDNService sdnService;
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    SnapshotService snapshotService;
    
    @Inject
    BackupService backupService;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    VMIdService vmIdService;
    
    @Inject
    MoxxieConfig config;
    
    @Context
    UriInfo uriInfo;

    @GET
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "List all VMs", description = "Get a list of all VMs in the Proxmox cluster with optional filtering by tags, client, node, and status")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VMs retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VMs",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listVMs(
            @Parameter(description = "Filter by tags (comma-separated, AND logic)")
            @QueryParam("tags") String tags,
            @Parameter(description = "Filter by client (convenience filter for client:<name> tag)")
            @QueryParam("client") String client,
            @Parameter(description = "Filter by node name")
            @QueryParam("node") String node,
            @Parameter(description = "Filter by status (running, stopped)")
            @QueryParam("status") String status,
            @Parameter(description = "Filter by specific VM IDs (comma-separated)")
            @QueryParam("vmIds") String vmIds,
            @Parameter(description = "Filter by VM name pattern (e.g., workshop-*, *-prod)")
            @QueryParam("namePattern") String namePattern,
            @Parameter(description = "Number of results (default: 100)")
            @DefaultValue("100") @QueryParam("limit") int limit,
            @Parameter(description = "Pagination offset")
            @DefaultValue("0") @QueryParam("offset") int offset) {
        // Parse tag filter
        List<String> tagFilter = null;
        if (tags != null && !tags.isEmpty()) {
            tagFilter = List.of(tags.split(","));
        }
        
        // Get filtered VMs from service
        List<VMResponse> vms = vmService.listVMsWithFilters(tagFilter, client, node, status, null);
        
        // Apply additional filters for vmIds
        if (vmIds != null && !vmIds.isEmpty()) {
            Set<Integer> vmIdSet = new HashSet<>();
            for (String id : vmIds.split(",")) {
                try {
                    vmIdSet.add(Integer.parseInt(id.trim()));
                } catch (NumberFormatException e) {
                    // Skip invalid IDs
                }
            }
            vms = vms.stream()
                .filter(vm -> vmIdSet.contains(vm.vmid()))
                .collect(Collectors.toList());
        }
        
        // Apply name pattern filter
        if (namePattern != null && !namePattern.isEmpty()) {
            Pattern pattern = Pattern.compile(namePattern.replace("*", ".*"));
            vms = vms.stream()
                .filter(vm -> pattern.matcher(vm.name()).matches())
                .collect(Collectors.toList());
        }
        
        // Apply pagination
        List<VMResponse> paginatedVMs = vms.stream()
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
        
        return Response.ok(paginatedVMs).build();
    }

    @GET
    @Path("/{vmId}")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "Get VM details", description = "Get detailed information about a specific VM")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VM retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {
        VMResponse response = findVmById(vmId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/{vmId}/debug")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "Debug VM info", description = "Get raw JSON from Proxmox cluster resources for a specific VM")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Debug info retrieved successfully"),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve debug info",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response debugVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {
        // Get ticket and CSRF token
        String ticket = ticketManager.getTicket();
        String csrfToken = ticketManager.getCsrfToken();
        
        // Call Proxmox cluster resources directly
        JsonNode clusterResources = proxmoxClient.getClusterResources(ticket, csrfToken, "vm");
        
        // Find the specific VM in the results
        if (clusterResources != null && clusterResources.has("data") && clusterResources.get("data").isArray()) {
            for (JsonNode resource : clusterResources.get("data")) {
                if (resource.has("vmid") && resource.get("vmid").asInt() == vmId) {
                    // Return the raw JSON for this VM
                    return Response.ok(resource).build();
                }
            }
        }
        
        throw ProxmoxException.notFound("VM", String.valueOf(vmId));
    }

    @POST
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Create new VM", description = "Create a new virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "VM created successfully",
            content = @Content(schema = @Schema(implementation = CreateVMResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "VM ID already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createVM(
            @RequestBody(description = "VM creation request", required = true,
                content = @Content(schema = @Schema(implementation = CreateVMRequestDTO.class)))
            @Valid CreateVMRequestDTO request) {
        try {
            // Validate node exists
            if (request.node() == null || request.node().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Node is required"))
                        .build();
            }
            
            // Convert DTO to client request
            CreateVMRequest clientRequest = new CreateVMRequest();
            
            // Generate VM ID if not provided
            int vmId = request.vmId() != null ? request.vmId() : vmIdService.getNextAvailableVmId(null);
            clientRequest.setVmid(vmId);
            clientRequest.setName(request.name());
            clientRequest.setCores(request.cores());
            clientRequest.setMemory(request.memoryMB());
            
            // Handle multiple networks
            List<com.coffeesprout.api.dto.NetworkConfig> networks = request.networks();
            
            // Backward compatibility: if networks is null/empty but network is set, convert it
            if ((networks == null || networks.isEmpty()) && request.network() != null) {
                CreateVMRequestDTO.NetworkConfig oldNet = request.network();
                networks = List.of(new com.coffeesprout.api.dto.NetworkConfig(
                    "virtio", 
                    oldNet.bridge(), 
                    oldNet.vlan(),
                    null, null, null, null, null, null
                ));
            }
            
            // Set up networks
            if (networks != null) {
                for (int i = 0; i < Math.min(networks.size(), 8); i++) {
                    com.coffeesprout.api.dto.NetworkConfig net = networks.get(i);
                    String netString = net.toProxmoxString();
                    
                    switch (i) {
                        case 0 -> clientRequest.setNet0(netString);
                        case 1 -> clientRequest.setNet1(netString);
                        case 2 -> clientRequest.setNet2(netString);
                        case 3 -> clientRequest.setNet3(netString);
                        case 4 -> clientRequest.setNet4(netString);
                        case 5 -> clientRequest.setNet5(netString);
                        case 6 -> clientRequest.setNet6(netString);
                        case 7 -> clientRequest.setNet7(netString);
                    }
                }
            }
            
            // Set start on boot
            if (request.startOnBoot() != null && request.startOnBoot()) {
                clientRequest.setOnboot(1);
            }
            
            // Set pool if specified
            if (request.pool() != null && !request.pool().isEmpty()) {
                clientRequest.setPool(request.pool());
            }
            
            // Set boot order if specified
            if (request.bootOrder() != null && !request.bootOrder().isEmpty()) {
                clientRequest.setBoot(request.bootOrder());
            }
            
            // Add tags from the request
            if (request.tags() != null && !request.tags().isEmpty()) {
                clientRequest.setTags(String.join(",", request.tags()));
            }
            
            // Set CPU type (use request value or default)
            if (request.cpuType() != null && !request.cpuType().isEmpty()) {
                clientRequest.setCpu(request.cpuType());
            } else {
                clientRequest.setCpu(config.proxmox().defaultCpuType());
            }
            
            // Set VGA type (use request value or default)
            if (request.vgaType() != null && !request.vgaType().isEmpty()) {
                clientRequest.setVga(request.vgaType());
            } else {
                clientRequest.setVga(config.proxmox().defaultVgaType());
            }
            
            // Handle disk configurations
            if (request.disks() != null && !request.disks().isEmpty()) {
                // Use the new disk configuration
                for (DiskConfig disk : request.disks()) {
                    String diskString = disk.toProxmoxString();
                    log.info("Setting disk {} with config: {}", disk.getParameterName(), diskString);
                    
                    // Set the disk based on its interface and slot
                    switch (disk.interfaceType()) {
                        case SCSI:
                            switch (disk.slot()) {
                                case 0:
                                    clientRequest.setScsi0(diskString);
                                    break;
                                case 1:
                                    clientRequest.setScsi1(diskString);
                                    break;
                                case 2:
                                    clientRequest.setScsi2(diskString);
                                    break;
                                case 3:
                                    clientRequest.setScsi3(diskString);
                                    break;
                                case 4:
                                    clientRequest.setScsi4(diskString);
                                    break;
                                case 5:
                                    clientRequest.setScsi5(diskString);
                                    break;
                                default:
                                    log.warn("SCSI slot {} is not supported yet (max slot 5)", disk.slot());
                                    break;
                            }
                            break;
                        case VIRTIO:
                            // TODO: Add support for virtio disks
                            break;
                        case IDE:
                            // TODO: Add support for IDE disks
                            break;
                        case SATA:
                            // TODO: Add support for SATA disks
                            break;
                    }
                }
            } else if (request.diskGB() != null && request.diskGB() > 0) {
                // Fallback to legacy disk configuration
                // TODO: Make storage configurable - for now use local-zfs
                clientRequest.setScsi0("local-zfs:" + request.diskGB());
            }
            
            // Create the VM
            CreateVMResponse response = vmService.createVM(request.node(), clientRequest, null);
            
            // Build location URI
            URI location = UriBuilder.fromResource(VMResource.class)
                    .path(String.valueOf(vmId))
                    .build();
            
            return Response.created(location)
                    .entity(response)
                    .build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create VM", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create VM: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/cloud-init")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Create VM from cloud image", 
               description = "Create a new VM from a cloud-init image with automatic disk import")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "VM created successfully",
            content = @Content(schema = @Schema(implementation = CreateVMResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "VM ID already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCloudInitVM(
            @RequestBody(description = "Cloud-init VM creation request", required = true,
                content = @Content(schema = @Schema(implementation = CloudInitVMRequest.class)))
            @Valid CloudInitVMRequest request) {
        log.info("Creating cloud-init VM {} from image {}", request.name(), request.imageSource());
        
        // Delegate to VMService which handles VMID allocation, creation, and migration
        CreateVMResponse response = vmService.createCloudInitVM(request, null);
        
        // Build location URI
        URI location = uriInfo.getAbsolutePathBuilder()
                .replacePath("/api/v1/vms/{vmId}")
                .build(response.getVmid());
        
        return Response.created(location)
                .entity(response)
                .build();
    }

    @DELETE
    @Path("/{vmId}")
    @SafeMode(operation = SafeMode.Operation.DELETE)
    @Operation(summary = "Delete VM", description = "Delete a virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "VM deleted successfully"),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to delete VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force delete even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        VMResponse vm = findVmById(vmId);
        
        // Delete the VM
        vmService.deleteVM(vm.node(), vmId, null);
        
        return Response.noContent().build();
    }
    
    @GET
    @Path("/{vmId}/detail")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "Get detailed VM information", description = "Get detailed information including network configuration")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VM details retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMDetailResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VM details",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVMDetail(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {
        VMResponse vm = findVmById(vmId);
        
        // Get detailed VM configuration
        log.debug("Getting config for VM {} on node '{}'", vmId, vm.node());
        Map<String, Object> config = vmService.getVMConfig(vm.node(), vmId, null);
        
        // Parse network interfaces from config
        List<VMDetailResponse.NetworkInterfaceInfo> networkInterfaces = parseNetworkInterfaces(config);
        
        // Parse disk information from config
        List<DiskInfo> disks = parseDiskInfo(config);
        long totalDiskSize = calculateTotalDiskSize(disks);
        
        // Get VM tags
        List<String> tags = new ArrayList<>(tagService.getVMTags(vmId, null));
        
        VMDetailResponse response = new VMDetailResponse(
            vm.vmid(),
            vm.name() != null ? vm.name() : "VM-" + vm.vmid(),
            vm.node(),
            vm.status(),
            vm.cpus(),
            vm.maxmem(),
            vm.maxdisk(),
            totalDiskSize,
            disks,
            vm.uptime(),
            vm.type(),
            networkInterfaces,
            tags,
            config
        );
        
        return Response.ok(response).build();
    }
    
    private List<VMDetailResponse.NetworkInterfaceInfo> parseNetworkInterfaces(Map<String, Object> config) {
        List<VMDetailResponse.NetworkInterfaceInfo> interfaces = new ArrayList<>();
        
        // Look for network interfaces (net0, net1, etc.)
        for (String key : config.keySet()) {
            if (key.startsWith("net") && key.matches("net\\d+")) {
                String rawConfig = String.valueOf(config.get(key));
                VMDetailResponse.NetworkInterfaceInfo iface = parseNetworkInterface(key, rawConfig);
                if (iface != null) {
                    interfaces.add(iface);
                }
            }
        }
        
        return interfaces;
    }
    
    private VMDetailResponse.NetworkInterfaceInfo parseNetworkInterface(String name, String rawConfig) {
        try {
            // Parse network config string (e.g., "virtio=BC:24:11:5E:7D:2C,bridge=vmbr0,tag=101")
            Map<String, String> params = new HashMap<>();
            String model = "virtio";
            String macAddress = null;
            
            // Split by comma and parse key=value pairs
            String[] parts = rawConfig.split(",");
            for (String part : parts) {
                if (part.contains("=")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) {
                        params.put(kv[0].trim(), kv[1].trim());
                    }
                } else if (part.matches("[0-9A-Fa-f:]+") && part.contains(":")) {
                    // Likely a MAC address
                    macAddress = part.trim();
                }
            }
            
            // Extract model from the first part
            if (parts.length > 0 && parts[0].contains("=")) {
                String[] modelParts = parts[0].split("=", 2);
                if (modelParts.length == 2) {
                    model = modelParts[0].trim();
                    macAddress = modelParts[1].trim();
                }
            }
            
            String bridge = params.get("bridge");
            Integer vlan = params.containsKey("tag") ? Integer.parseInt(params.get("tag")) : null;
            boolean firewall = "1".equals(params.get("firewall"));
            
            return new VMDetailResponse.NetworkInterfaceInfo(
                name,
                macAddress,
                bridge,
                vlan,
                model,
                firewall,
                rawConfig
            );
        } catch (Exception e) {
            log.warn("Failed to parse network interface config: " + rawConfig, e);
            return null;
        }
    }
    
    /**
     * Find a VM by ID with proper error handling.
     * Centralizes the common pattern of listing VMs and filtering by ID.
     * 
     * @param vmId VM ID to find
     * @return VM response if found
     * @throws WebApplicationException with 404 status if VM not found
     */
    private VMResponse findVmById(int vmId) {
        List<VMResponse> vms = vmService.listVMs(null);
        return vms.stream()
            .filter(v -> v.vmid() == vmId)
            .findFirst()
            .orElseThrow(() -> ProxmoxException.notFound("VM", String.valueOf(vmId)));
    }
    
    @GET
    @Path("/{vmId}/status")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "Get VM status", description = "Get detailed status information about a specific VM")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VM status retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMStatusDetailResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VM status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVMStatus(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {
        // First, find the VM to get its node
        VMResponse vm = findVmById(vmId);
        
        // Get detailed status
        VMStatusResponse status = vmService.getVMStatus(vm.node(), vmId, null);
        VMStatusResponse.VMStatusData data = status.getData();
        
        // Calculate memory percentage
        double memoryPercent = data.getMaxmem() > 0 ? 
            (double) data.getMem() / data.getMaxmem() * 100 : 0;
        
        VMStatusDetailResponse response = new VMStatusDetailResponse(
            data.getVmid(),
            data.getName(),
            data.getStatus(),
            data.getQmpstatus(),
            data.getCpu() * 100, // Convert to percentage
            data.getCpus(),
            data.getMem(),
            data.getMaxmem(),
            memoryPercent,
            data.getDiskread(),
            data.getDiskwrite(),
            data.getDisk(),
            data.getMaxdisk(),
            data.getNetin(),
            data.getNetout(),
            data.getUptime(),
            data.getPid(),
            "running".equals(data.getStatus()),
            data.getLock(),
            data.getAgentStatus()
        );
        
        return Response.ok(response).build();
    }
    
    @POST
    @Path("/{vmId}/start")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Start VM", description = "Start a stopped virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM start initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is already running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to start VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response startVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force start even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if ("running".equals(vm.status())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is already running"))
                        .build();
            }
            
            vmService.startVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM start initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to start VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to start VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/stop")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Stop VM", description = "Stop a running virtual machine (forced stop)")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM stop initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is not running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to stop VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response stopVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force stop even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if ("stopped".equals(vm.status())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.stopVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM stop initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to stop VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to stop VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/reboot")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Reboot VM", description = "Reboot a running virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM reboot initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is not running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to reboot VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response rebootVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force reboot even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.status())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.rebootVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM reboot initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to reboot VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to reboot VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/shutdown")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Shutdown VM", description = "Gracefully shutdown a virtual machine using ACPI")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM shutdown initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is not running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to shutdown VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response shutdownVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force shutdown even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.status())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.shutdownVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM shutdown initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to shutdown VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to shutdown VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/suspend")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Suspend VM", description = "Suspend a running virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM suspend initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is not running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to suspend VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response suspendVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force suspend even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.status())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.suspendVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM suspend initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to suspend VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to suspend VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/resume")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Resume VM", description = "Resume a suspended virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "VM resume initiated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "VM is not suspended",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to resume VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response resumeVM(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force resume even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            // Check if VM is suspended - Proxmox might report this as "stopped" with suspend disk
            // For now, we'll attempt resume regardless
            vmService.resumeVM(vm.node(), vmId, null);
            return Response.accepted()
                    .entity(new ErrorResponse("VM resume initiated"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to resume VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to resume VM: " + e.getMessage()))
                    .build();
        }
    }
    
    @GET
    @Path("/{vmId}/tags")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "Get VM tags", description = "Get all tags assigned to a virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tags retrieved successfully",
            content = @Content(schema = @Schema(implementation = TagsResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve tags",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getVMTags(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId) {
        try {
            var tags = tagService.getVMTags(vmId, null);
            return Response.ok(new TagsResponse(tags)).build();
        } catch (Exception e) {
            log.error("Failed to get tags for VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get tags: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/tags")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Add tag to VM", description = "Add a new tag to a virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tag added successfully"),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid tag",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to add tag",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addTag(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Force add even if not managed by Moxxie")
            @QueryParam("force") boolean force,
            @RequestBody(description = "Tag to add", required = true,
                content = @Content(schema = @Schema(implementation = TagRequest.class)))
            TagRequest request) {
        try {
            if (request.tag() == null || request.tag().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Tag cannot be empty"))
                        .build();
            }
            
            tagService.addTag(vmId, request.tag(), null);
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Failed to add tag to VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to add tag: " + e.getMessage()))
                    .build();
        }
    }
    
    @DELETE
    @Path("/{vmId}/tags/{tag}")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Remove tag from VM", description = "Remove a tag from a virtual machine")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Tag removed successfully"),
        @APIResponse(responseCode = "404", description = "VM or tag not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Forbidden - Safe mode violation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to remove tag",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response removeTag(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Parameter(description = "Tag to remove", required = true)
            @PathParam("tag") String tag,
            @Parameter(description = "Force remove even if not managed by Moxxie")
            @QueryParam("force") boolean force) {
        try {
            tagService.removeTag(vmId, tag, null);
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Failed to remove tag from VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to remove tag: " + e.getMessage()))
                    .build();
        }
    }
    
    private List<DiskInfo> parseDiskInfo(Map<String, Object> config) {
        List<DiskInfo> disks = new ArrayList<>();
        
        // Look for disk interfaces: scsi, virtio, ide, sata
        String[] diskPrefixes = {"scsi", "virtio", "ide", "sata"};
        
        for (String prefix : diskPrefixes) {
            for (int i = 0; i < 30; i++) { // Support up to 30 disks per type
                String key = prefix + i;
                if (config.containsKey(key)) {
                    String diskSpec = config.get(key).toString();
                    // Skip CD-ROM/media entries
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
            // Parse storage backend and disk path
            String[] parts = diskSpec.split(",");
            if (parts.length == 0) return null;
            
            String[] storageParts = parts[0].split(":");
            if (storageParts.length < 2) return null;
            
            String storage = storageParts[0];
            String format = null;
            String sizeStr = null;
            Long sizeBytes = null;
            
            // Parse additional parameters
            for (String part : parts) {
                if (part.startsWith("format=")) {
                    format = part.substring(7);
                } else if (part.startsWith("size=")) {
                    sizeStr = part.substring(5);
                    sizeBytes = parseDiskSize(sizeStr);
                }
            }
            
            // If size wasn't in the spec, default to a reasonable size
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
            // Handle sizes like "20G", "100M", "1T"
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
                // Assume bytes if no unit
                return Long.parseLong(sizeStr);
            }
        } catch (Exception e) {
            log.warn("Failed to parse disk size: {}", sizeStr, e);
            return null;
        }
    }
    
    private long calculateTotalDiskSize(List<DiskInfo> disks) {
        return disks.stream()
            .mapToLong(disk -> disk.sizeBytes() != null ? disk.sizeBytes() : 0L)
            .sum();
    }
    
    // DTOs for tag operations
    public record TagsResponse(Set<String> tags) {}
    public record TagRequest(String tag) {}
    
    // SSH Key Management
    @PUT
    @Path("/{vmId}/ssh-keys")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set SSH keys on VM", 
               description = "Set SSH keys on an existing VM. Uses proper double URL encoding required by Proxmox API.")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "SSH keys set successfully"),
        @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to set SSH keys",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response setSSHKeys(
            @Parameter(description = "VM ID", required = true)
            @PathParam("vmId") int vmId,
            @Valid SetSSHKeysRequest request) {
        try {
            // Find the VM to get its node
            List<VMResponse> vms = vmService.listVMs(null);
            VMResponse vm = vms.stream()
                .filter(v -> v.vmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            log.info("Setting SSH keys for VM {}", vmId);
            
            // Use the direct SSH key method that does double encoding
            vmService.setSSHKeysDirect(vm.node(), vmId, request.sshKeys(), null);
            
            return Response.ok(Map.of(
                "success", true,
                "vmId", vmId,
                "message", "SSH keys set successfully"
            )).build();
            
        } catch (Exception e) {
            log.error("Failed to set SSH keys for VM {}", vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to set SSH keys: " + e.getMessage()))
                    .build();
        }
    }
    
    // Snapshot Management Endpoints
    
    @GET
    @Path("/{vmId}/snapshots")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "List VM snapshots", 
               description = "Get all snapshots for a specific VM")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Snapshots retrieved successfully",
            content = @Content(schema = @Schema(implementation = SnapshotResponse[].class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve snapshots",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listSnapshots(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId) {
        try {
            List<SnapshotResponse> snapshots = snapshotService.listSnapshots(vmId, null);
            return Response.ok(snapshots).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("VM not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to list snapshots for VM {}", vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list snapshots: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/snapshots")
    @SafeMode(value = true)  // Write operation
    @Operation(summary = "Create VM snapshot", 
               description = "Create a new snapshot of the VM's current state")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Snapshot creation started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid snapshot parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Snapshot name already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create snapshot",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createSnapshot(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId,
            @Valid CreateSnapshotRequest request) {
        try {
            TaskResponse task = snapshotService.createSnapshot(vmId, request, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("VM not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            } else if (e.getMessage().contains("already exists")) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to create snapshot for VM {}", vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create snapshot: " + e.getMessage()))
                    .build();
        }
    }
    
    @DELETE
    @Path("/{vmId}/snapshots/{snapshotName}")
    @SafeMode(value = true)  // Write operation
    @Operation(summary = "Delete VM snapshot", 
               description = "Delete a specific snapshot of the VM")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Snapshot deletion started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "404", description = "VM or snapshot not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to delete snapshot",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteSnapshot(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId,
            @Parameter(description = "Snapshot name", required = true, example = "backup-2024-01-15")
            @PathParam("snapshotName") String snapshotName) {
        try {
            TaskResponse task = snapshotService.deleteSnapshot(vmId, snapshotName, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete snapshot {} for VM {}", snapshotName, vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete snapshot: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/snapshots/{snapshotName}/rollback")
    @SafeMode(value = true)  // Write operation - potentially dangerous
    @Operation(summary = "Rollback VM to snapshot", 
               description = "Rollback the VM to a previous snapshot state. WARNING: This will discard all changes made after the snapshot.")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Rollback started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "404", description = "VM or snapshot not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to rollback snapshot",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response rollbackSnapshot(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId,
            @Parameter(description = "Snapshot name", required = true, example = "backup-2024-01-15")
            @PathParam("snapshotName") String snapshotName) {
        try {
            TaskResponse task = snapshotService.rollbackSnapshot(vmId, snapshotName, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse(e.getMessage()))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to rollback to snapshot {} for VM {}", snapshotName, vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to rollback snapshot: " + e.getMessage()))
                    .build();
        }
    }
    
    // Backup Management Endpoints
    
    @GET
    @Path("/{vmId}/backups")
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "List VM backups", 
               description = "Get all backups for a specific VM across all storage locations")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Backups retrieved successfully",
            content = @Content(schema = @Schema(implementation = BackupResponse[].class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve backups",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listBackups(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId) {
        try {
            List<BackupResponse> backups = backupService.listBackups(vmId, null);
            return Response.ok(backups).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("VM not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to list backups for VM {}", vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list backups: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/backup")
    @SafeMode(value = true)  // Write operation
    @Operation(summary = "Create VM backup", 
               description = "Create a new backup of the VM using vzdump")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Backup creation started",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid backup parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to create backup",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createBackup(
            @Parameter(description = "VM ID", required = true, example = "100")
            @PathParam("vmId") int vmId,
            @Valid BackupRequest request) {
        try {
            TaskResponse task = backupService.createBackup(vmId, request, null);
            return Response.accepted(task).build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("VM not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to create backup for VM {}", vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to create backup: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/{vmId}/clone")
    @SafeMode(value = true, operation = SafeMode.Operation.WRITE)
    @Operation(summary = "Clone VM", description = "Clone a VM from a template or existing VM")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Clone operation initiated",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid clone request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Template VM not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Target VM ID already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to clone VM",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response cloneVM(
            @Parameter(description = "Template VM ID", required = true)
            @PathParam("vmId") int templateId,
            @RequestBody(description = "Clone configuration", required = true)
            @Valid com.coffeesprout.api.dto.TemplateCloneRequest request) {
        try {
            // Use VMIdService for auto-generation if needed
            int newVmId = request.newVmId() != null ? request.newVmId() : vmIdService.getNextAvailableVmId(null);
            
            VMResponse templateVm = findVmById(templateId);
            
            TaskStatusResponse task = vmService.cloneVM(
                templateVm.node(),
                templateId,
                newVmId,
                request.name(),
                request.description(),
                request.fullClone() != null ? request.fullClone() : false,
                request.pool(),
                null, // snapname
                request.targetStorage(),
                request.targetNode(),
                null
            );
            
            TaskResponse response = new TaskResponse(
                task.getData(),
                "VM clone operation initiated successfully"
            );
            
            return Response.accepted(response).build();
            
        } catch (WebApplicationException e) {
            // Re-throw WebApplicationExceptions (like 404 from findVmById)
            throw e;
        } catch (Exception e) {
            log.error("Failed to clone VM {} to {}", templateId, request.newVmId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to clone VM: " + e.getMessage()))
                    .build();
        }
    }
}