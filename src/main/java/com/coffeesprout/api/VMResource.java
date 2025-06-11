package com.coffeesprout.api;

import com.coffeesprout.api.dto.CreateVMRequestDTO;
import com.coffeesprout.api.dto.DiskInfo;
import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.VMDetailResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.dto.VMStatusDetailResponse;
import com.coffeesprout.client.CreateVMRequest;
import com.coffeesprout.client.CreateVMResponse;
import com.coffeesprout.client.VM;
import com.coffeesprout.client.VMStatusResponse;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.SDNService;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import com.coffeesprout.service.TicketManager;
import com.coffeesprout.client.ProxmoxClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
    TicketManager ticketManager;

    @GET
    @SafeMode(value = false)  // Read operation
    @Operation(summary = "List all VMs", description = "Get a list of all VMs in the Proxmox cluster with optional filtering")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "VMs retrieved successfully",
            content = @Content(schema = @Schema(implementation = VMResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve VMs",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listVMs(
            @Parameter(description = "Filter by node name")
            @QueryParam("node") String node,
            @Parameter(description = "Filter by status (running, stopped)")
            @QueryParam("status") String status,
            @Parameter(description = "Number of results (default: 100)")
            @DefaultValue("100") @QueryParam("limit") int limit,
            @Parameter(description = "Pagination offset")
            @DefaultValue("0") @QueryParam("offset") int offset) {
        try {
            List<VM> vms = vmService.listVMs(null);
            
            // Apply filters
            var filteredVMs = vms.stream();
            
            if (node != null && !node.isEmpty()) {
                filteredVMs = filteredVMs.filter(vm -> node.equals(vm.getNode()));
            }
            
            if (status != null && !status.isEmpty()) {
                filteredVMs = filteredVMs.filter(vm -> status.equals(vm.getStatus()));
            }
            
            // Apply pagination
            List<VMResponse> vmResponses = filteredVMs
                .skip(offset)
                .limit(limit)
                .map(vm -> new VMResponse(
                    vm.getVmid(),
                    vm.getName() != null ? vm.getName() : "VM-" + vm.getVmid(),
                    vm.getNode(),
                    vm.getStatus(),
                    vm.getCpus(),
                    vm.getMaxmem(),
                    vm.getMaxdisk(),
                    vm.getUptime(),
                    vm.getType()
                ))
                .collect(Collectors.toList());
            
            return Response.ok(vmResponses).build();
        } catch (Exception e) {
            log.error("Failed to list VMs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list VMs: " + e.getMessage()))
                    .build();
        }
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
        try {
            // First, get all VMs to find the one we're looking for
            List<VM> vms = vmService.listVMs(null);
            
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            VMResponse response = new VMResponse(
                vm.getVmid(),
                vm.getName() != null ? vm.getName() : "VM-" + vm.getVmid(),
                vm.getNode(),
                vm.getStatus(),
                vm.getCpus(),
                vm.getMaxmem(),
                vm.getMaxdisk(),
                vm.getUptime(),
                vm.getType()
            );
            
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to get VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get VM: " + e.getMessage()))
                    .build();
        }
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
        try {
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
            
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("VM not found in cluster resources: " + vmId))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get debug info for VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get debug info: " + e.getMessage()))
                    .build();
        }
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
            int vmId = request.vmId() != null ? request.vmId() : generateVMId();
            clientRequest.setVmid(vmId);
            clientRequest.setName(request.name());
            clientRequest.setCores(request.cores());
            clientRequest.setMemory(request.memoryMB());
            
            // Build network configuration
            if (request.network() != null) {
                String netConfig = String.format("virtio,bridge=%s", request.network().bridge());
                
                // Only use manually specified VLAN
                if (request.network().vlan() != null) {
                    netConfig += ",tag=" + request.network().vlan();
                }
                
                clientRequest.setNet0(netConfig);
            }
            
            // Set start on boot
            if (request.startOnBoot() != null && request.startOnBoot()) {
                clientRequest.setOnboot(1);
            }
            
            // Set pool if specified
            if (request.pool() != null && !request.pool().isEmpty()) {
                clientRequest.setPool(request.pool());
            }
            
            // Create the VM
            CreateVMResponse response = vmService.createVM(request.node(), clientRequest, null);
            
            // Tag the VM as managed by Moxxie
            try {
                tagService.addTag(vmId, "moxxie");
                log.info("Tagged new VM {} as moxxie-managed", vmId);
            } catch (Exception e) {
                log.warn("Failed to tag new VM {} as moxxie-managed: {}", vmId, e.getMessage());
            }
            
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
        try {
            // First, find the VM to get its node
            List<VM> vms = vmService.listVMs(null);
            
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            // Delete the VM
            vmService.deleteVM(vm.getNode(), vmId, null);
            
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete VM: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete VM: " + e.getMessage()))
                    .build();
        }
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
        try {
            // Get basic VM info
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            // Get detailed VM configuration
            log.debug("Getting config for VM {} on node '{}'", vmId, vm.getNode());
            Map<String, Object> config = vmService.getVMConfig(vm.getNode(), vmId, null);
            
            // Parse network interfaces from config
            List<VMDetailResponse.NetworkInterfaceInfo> networkInterfaces = parseNetworkInterfaces(config);
            
            // Parse disk information from config
            List<DiskInfo> disks = parseDiskInfo(config);
            long totalDiskSize = calculateTotalDiskSize(disks);
            
            // Get VM tags
            List<String> tags = new ArrayList<>(tagService.getVMTags(vmId));
            
            VMDetailResponse response = new VMDetailResponse(
                vm.getVmid(),
                vm.getName() != null ? vm.getName() : "VM-" + vm.getVmid(),
                vm.getNode(),
                vm.getStatus(),
                vm.getCpus(),
                vm.getMaxmem(),
                vm.getMaxdisk(),
                totalDiskSize,
                disks,
                vm.getUptime(),
                vm.getType(),
                networkInterfaces,
                tags,
                config
            );
            
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to get VM details: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get VM details: " + e.getMessage()))
                    .build();
        }
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
    
    private int generateVMId() {
        // Generate a random VM ID between 100 and 999999
        return ThreadLocalRandom.current().nextInt(100, 1000000);
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
        try {
            // First, find the VM to get its node
            List<VM> vms = vmService.listVMs(null);
            
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            // Get detailed status
            VMStatusResponse status = vmService.getVMStatus(vm.getNode(), vmId, null);
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
        } catch (Exception e) {
            log.error("Failed to get VM status: " + vmId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get VM status: " + e.getMessage()))
                    .build();
        }
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if ("running".equals(vm.getStatus())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is already running"))
                        .build();
            }
            
            vmService.startVM(vm.getNode(), vmId, null);
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if ("stopped".equals(vm.getStatus())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.stopVM(vm.getNode(), vmId, null);
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.getStatus())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.rebootVM(vm.getNode(), vmId, null);
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.getStatus())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.shutdownVM(vm.getNode(), vmId, null);
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            if (!"running".equals(vm.getStatus())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("VM is not running"))
                        .build();
            }
            
            vmService.suspendVM(vm.getNode(), vmId, null);
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
            List<VM> vms = vmService.listVMs(null);
            VM vm = vms.stream()
                .filter(v -> v.getVmid() == vmId)
                .findFirst()
                .orElse(null);
            
            if (vm == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("VM not found: " + vmId))
                        .build();
            }
            
            // Check if VM is suspended - Proxmox might report this as "stopped" with suspend disk
            // For now, we'll attempt resume regardless
            vmService.resumeVM(vm.getNode(), vmId, null);
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
            var tags = tagService.getVMTags(vmId);
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
            
            tagService.addTag(vmId, request.tag());
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
            tagService.removeTag(vmId, tag);
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
}