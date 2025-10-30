package com.coffeesprout.api;

import com.coffeesprout.api.dto.AnsibleInventoryResponse;
import com.coffeesprout.service.AnsibleInventoryService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;

@Path("/api/v1/ansible")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Ansible Integration", description = "Ansible dynamic inventory and integration endpoints")
@RunOnVirtualThread
public class AnsibleInventoryResource {

    private static final Logger LOG = Logger.getLogger(AnsibleInventoryResource.class);

    @Inject
    AnsibleInventoryService ansibleInventoryService;

    @GET
    @Path("/inventory")
    @Operation(
        summary = "Get Ansible dynamic inventory",
        description = "Generate Ansible dynamic inventory from Moxxie-managed VMs. " +
                     "Supports filtering by tags, client, environment, node, and status. " +
                     "Returns JSON format by default, or INI format if requested."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Inventory generated successfully",
            content = @Content(schema = @Schema(implementation = AnsibleInventoryResponse.class))
        ),
        @APIResponse(
            responseCode = "500",
            description = "Failed to generate inventory"
        )
    })
    public Response getInventory(
        @Parameter(description = "Inventory format: json or ini", example = "json")
        @QueryParam("format") @DefaultValue("json") String format,

        @Parameter(description = "Filter by tags (comma-separated, AND logic)", example = "env-prod,client-acme")
        @QueryParam("tags") String tags,

        @Parameter(description = "Filter by client (convenience for client-<name> tag)", example = "acme")
        @QueryParam("client") String client,

        @Parameter(description = "Filter by environment (convenience for env-<env> tag)", example = "prod")
        @QueryParam("environment") String environment,

        @Parameter(description = "Filter by node name", example = "hv7")
        @QueryParam("node") String node,

        @Parameter(description = "Filter by VM status", example = "running")
        @QueryParam("status") String status,

        @Parameter(description = "Include only VMs with moxxie tag", example = "true")
        @QueryParam("moxxieOnly") @DefaultValue("true") boolean moxxieOnly
    ) {
        LOG.infof("Generating Ansible inventory: format=%s, tags=%s, client=%s, environment=%s, node=%s, status=%s, moxxieOnly=%s",
                 format, tags, client, environment, node, status, moxxieOnly);

        try {
            List<String> tagList = null;
            if (tags != null && !tags.isBlank()) {
                tagList = List.of(tags.split(","));
            }

            if ("ini".equalsIgnoreCase(format)) {
                String iniInventory = ansibleInventoryService.generateINIInventory(
                    tagList, client, environment, node, status, moxxieOnly, null
                );
                return Response.ok(iniInventory, MediaType.TEXT_PLAIN).build();
            } else {
                AnsibleInventoryResponse jsonInventory = ansibleInventoryService.generateJSONInventory(
                    tagList, client, environment, node, status, moxxieOnly, null
                );
                return Response.ok(jsonInventory).build();
            }
        } catch (Exception e) {
            LOG.error("Failed to generate Ansible inventory", e);
            return Response.serverError()
                          .entity("Failed to generate inventory: " + e.getMessage())
                          .build();
        }
    }
}
