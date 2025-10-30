package com.coffeesprout.api;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.NetworkInterfaceResponse;
import com.coffeesprout.client.NetworkInterface;
import com.coffeesprout.service.NetworkService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v1/networks")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Networks", description = "Network management endpoints")
public class NetworkResource {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkResource.class);

    @Inject
    NetworkService networkService;

    @GET
    @Operation(summary = "List network interfaces", description = "Get network interfaces from all nodes or a specific node")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Networks retrieved successfully",
            content = @Content(schema = @Schema(implementation = NetworkInterfaceResponse[].class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve networks",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listNetworks(
            @Parameter(description = "Filter by node name")
            @QueryParam("node") String node,
            @Parameter(description = "Filter by interface type (bridge, bond, eth, vlan)")
            @QueryParam("type") String type,
            @Parameter(description = "Filter only active interfaces")
            @QueryParam("active") Boolean active) {
        List<NetworkInterface> networks;

        if (node != null && !node.isEmpty()) {
            networks = networkService.getNodeNetworks(node, null);
        } else {
            networks = networkService.getAllNetworks(null);
        }

        // Apply filters
        var filteredNetworks = networks.stream();

        if (type != null && !type.isEmpty()) {
            filteredNetworks = filteredNetworks.filter(net -> type.equals(net.getType()));
        }

        if (active != null) {
            int activeValue = active ? 1 : 0;
            filteredNetworks = filteredNetworks.filter(net ->
                net.getActive() != null && net.getActive() == activeValue);
        }

        List<NetworkInterfaceResponse> responses = filteredNetworks
            .map(net -> new NetworkInterfaceResponse(
                net.getIface(),
                net.getType(),
                net.getMethod(),
                net.getAddress(),
                net.getNetmask(),
                net.getGateway(),
                net.getBridge_ports(),
                net.getBridge_vlan_aware() != null && net.getBridge_vlan_aware() == 1,
                net.getCidr(),
                net.getComments(),
                net.getActive() != null && net.getActive() == 1,
                net.getAutostart() != null && net.getAutostart() == 1,
                node // Add node info if querying specific node
            ))
            .collect(Collectors.toList());

        return Response.ok(responses).build();
    }
}
