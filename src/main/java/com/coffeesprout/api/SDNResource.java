package com.coffeesprout.api;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.client.NetworkZone;
import com.coffeesprout.client.NetworkZonesResponse;
import com.coffeesprout.client.VNet;
import com.coffeesprout.client.VNetsResponse;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.SDNService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/v1/sdn")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SDN", description = "Software Defined Networking management")
public class SDNResource {
    
    private static final Logger LOG = Logger.getLogger(SDNResource.class);
    
    @Inject
    SDNService sdnService;
    
    @GET
    @Path("/zones")
    @Operation(summary = "List SDN zones", description = "List all available SDN zones in the cluster")
    @APIResponse(responseCode = "200", description = "List of SDN zones",
        content = @Content(schema = @Schema(implementation = SDNZoneResponseDTO.class, type = SchemaType.ARRAY)))
    @APIResponse(responseCode = "500", description = "Error listing zones")
    public Response listZones() {
        try {
            NetworkZonesResponse zones = sdnService.listZones(null);
            
            List<SDNZoneResponseDTO> response = zones.getData().stream()
                .map(zone -> new SDNZoneResponseDTO(
                    zone.getZone(),
                    zone.getType(),
                    zone.getIpam(),
                    zone.getDns(),
                    zone.getNodes()
                ))
                .collect(Collectors.toList());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to list SDN zones", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/vnets")
    @Operation(summary = "List VNets", description = "List all VNets, optionally filtered by zone or client")
    @APIResponse(responseCode = "200", description = "List of VNets",
        content = @Content(schema = @Schema(implementation = VNetResponseDTO.class, type = SchemaType.ARRAY)))
    @APIResponse(responseCode = "500", description = "Error listing VNets")
    public Response listVNets(
        @Parameter(description = "Filter by SDN zone")
        @QueryParam("zone") String zone,
        @Parameter(description = "Filter by client ID")
        @QueryParam("client") String clientId
    ) {
        try {
            VNetsResponse vnets = sdnService.listVNets(zone, null);
            
            List<VNetResponseDTO> response = vnets.getData().stream()
                .filter(vnet -> clientId == null || 
                    (vnet.getAlias() != null && vnet.getAlias().equals(clientId)))
                .map(vnet -> new VNetResponseDTO(
                    vnet.getVnet(),
                    vnet.getZone(),
                    vnet.getTag(),
                    vnet.getAlias(),
                    vnet.getType()
                ))
                .collect(Collectors.toList());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to list VNets", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/vnets")
    @Operation(summary = "Create VNet", description = "Create a new VNet with explicit VLAN assignment")
    @APIResponse(responseCode = "201", description = "VNet created successfully",
        content = @Content(schema = @Schema(implementation = VNetResponseDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "500", description = "Error creating VNet")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public Response createVNet(CreateVNetRequestDTO request) {
        try {
            if (request.clientId() == null || request.clientId().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Client ID is required"))
                    .build();
            }
            
            // For now, require explicit VLAN tag in request
            if (request.vlanTag() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("VLAN tag is required for VNet creation"))
                    .build();
            }
            
            VNet vnet = sdnService.createVNetWithVlan(request.clientId(), request.project(), request.vlanTag(), null);
            
            VNetResponseDTO response = new VNetResponseDTO(
                vnet.getVnet(),
                vnet.getZone(),
                vnet.getTag(),
                vnet.getAlias(),
                vnet.getType()
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
            
        } catch (Exception e) {
            LOG.error("Failed to create VNet", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @DELETE
    @Path("/vnets/{vnetId}")
    @Operation(summary = "Delete VNet", description = "Delete an existing VNet")
    @APIResponse(responseCode = "204", description = "VNet deleted successfully")
    @APIResponse(responseCode = "404", description = "VNet not found")
    @APIResponse(responseCode = "500", description = "Error deleting VNet")
    @SafeMode(operation = SafeMode.Operation.DELETE)
    public Response deleteVNet(
        @Parameter(description = "VNet identifier", required = true)
        @PathParam("vnetId") String vnetId,
        @Parameter(description = "Force deletion even if in use")
        @QueryParam("force") boolean force
    ) {
        try {
            sdnService.deleteVNet(vnetId, null);
            return Response.noContent().build();
            
        } catch (Exception e) {
            LOG.error("Failed to delete VNet", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/vlan-assignments")
    @Operation(summary = "List VLAN assignments", 
                  description = "Get all VLAN assignments showing which VLANs are in use and their associated SDN VNets")
    @APIResponse(responseCode = "200", description = "List of VLAN assignments",
        content = @Content(schema = @Schema(implementation = VlanAssignmentDTO.class, type = SchemaType.ARRAY)))
    public Response listVlanAssignments(
        @Parameter(description = "Filter by VLAN range start", example = "100")
        @QueryParam("rangeStart") Integer rangeStart,
        @Parameter(description = "Filter by VLAN range end", example = "200")
        @QueryParam("rangeEnd") Integer rangeEnd,
        @Parameter(description = "Include only allocated VLANs", example = "true")
        @QueryParam("allocatedOnly") @DefaultValue("false") boolean allocatedOnly
    ) {
        try {
            // Get all VNets to build VLAN assignment map
            VNetsResponse vnets = sdnService.listVNets(null, null);
            
            // Build a map of VLAN to VNet assignments
            Map<Integer, List<VNet>> vlanToVnets = new HashMap<>();
            Map<Integer, String> vlanToClient = new HashMap<>();
            
            for (VNet vnet : vnets.getData()) {
                if (vnet.getTag() != null) {
                    vlanToVnets.computeIfAbsent(vnet.getTag(), k -> new ArrayList<>()).add(vnet);
                    if (vnet.getAlias() != null && !vnet.getAlias().isEmpty()) {
                        vlanToClient.put(vnet.getTag(), vnet.getAlias());
                    }
                }
            }
            
            // Determine range
            int start = rangeStart != null ? rangeStart : 100;
            int end = rangeEnd != null ? rangeEnd : 200;
            
            List<VlanAssignmentDTO> assignments = new ArrayList<>();
            
            for (int vlan = start; vlan <= end; vlan++) {
                List<VNet> vnetsForVlan = vlanToVnets.get(vlan);
                boolean isAllocated = vnetsForVlan != null && !vnetsForVlan.isEmpty();
                
                if (!allocatedOnly || isAllocated) {
                    List<String> vnetIds = isAllocated ? 
                        vnetsForVlan.stream().map(VNet::getVnet).collect(Collectors.toList()) : 
                        Collections.emptyList();
                    
                    String clientId = vlanToClient.get(vlan);
                    String status = isAllocated ? "allocated" : "available";
                    
                    assignments.add(new VlanAssignmentDTO(
                        vlan,
                        clientId,
                        vnetIds,
                        status,
                        null
                    ));
                }
            }
            
            return Response.ok(assignments).build();
            
        } catch (Exception e) {
            LOG.error("Failed to list VLAN assignments", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/clients/{clientId}/vlan")
    @Operation(summary = "Get client VLAN", description = "Get the VLAN assigned to a specific client")
    @APIResponse(responseCode = "200", description = "Client VLAN information",
        content = @Content(schema = @Schema(implementation = ClientVlanResponseDTO.class)))
    @APIResponse(responseCode = "404", description = "Client not found or no VLAN assigned")
    public Response getClientVlan(
        @Parameter(description = "Client identifier", required = true)
        @PathParam("clientId") String clientId
    ) {
        try {
            // Get allocated VLAN
            Integer vlanTag = sdnService.getOrAllocateVlan(clientId);
            
            // Get VNets for this client
            VNetsResponse vnets = sdnService.listVNets(null, null);
            List<String> clientVnetIds = vnets.getData().stream()
                .filter(vnet -> clientId.equals(vnet.getAlias()))
                .map(VNet::getVnet)
                .collect(Collectors.toList());
            
            ClientVlanResponseDTO response = new ClientVlanResponseDTO(
                clientId,
                vlanTag,
                clientVnetIds
            );
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Failed to get client VLAN", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/apply")
    @Operation(summary = "Apply SDN configuration", 
                  description = "Apply pending SDN configuration changes to the cluster")
    @APIResponse(responseCode = "200", description = "Configuration applied successfully")
    @APIResponse(responseCode = "500", description = "Error applying configuration")
    @SafeMode(operation = SafeMode.Operation.WRITE)
    public Response applyConfiguration() {
        try {
            sdnService.applySDNConfiguration(null);
            return Response.ok()
                .entity(new MessageResponse("SDN configuration applied successfully"))
                .build();
            
        } catch (Exception e) {
            LOG.error("Failed to apply SDN configuration", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/clients/{clientId}/isolation-check")
    @Operation(summary = "Check network isolation", 
                  description = "Verify network isolation for a client")
    @APIResponse(responseCode = "200", description = "Isolation check results")
    @APIResponse(responseCode = "404", description = "Client not found")
    public Response checkIsolation(
        @Parameter(description = "Client identifier", required = true)
        @PathParam("clientId") String clientId
    ) {
        try {
            // This is a placeholder for network isolation verification
            // In a real implementation, this would check:
            // 1. VLAN configuration on all nodes
            // 2. No cross-client communication possible
            // 3. Proper VLAN tagging on all interfaces
            
            return Response.ok()
                .entity(new MessageResponse("Network isolation check for client " + clientId + 
                    " would be performed here"))
                .build();
            
        } catch (Exception e) {
            LOG.error("Failed to check network isolation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    // Simple message response DTO
    public record MessageResponse(String message) {}
}