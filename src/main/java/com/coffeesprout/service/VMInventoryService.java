package com.coffeesprout.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.util.TagUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides read-only access to VM inventory data pulled from Proxmox.
 * Acts as a mediator so consumers do not need to depend directly on VMService.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMInventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(VMInventoryService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    TicketManager ticketManager;

    /**
     * List all VMs in the cluster irrespective of filters.
     */
    public List<VMResponse> listAll(@AuthTicket String ticket) {
        try {
            JsonNode resources = proxmoxClient.getClusterResources(ticket, ticketManager.getCsrfToken(), "vm");
            List<VMResponse> vms = new ArrayList<>();

            if (resources != null && resources.has("data")) {
                JsonNode dataArray = resources.get("data");

                for (JsonNode resource : dataArray) {
                    if (!"qemu".equals(resource.path("type").asText(""))) {
                        continue;
                    }

                    String tagsString = resource.path("tags").asText("");
                    Set<String> vmTags = tagsString.isEmpty()
                        ? Set.of()
                        : new HashSet<>(TagUtils.parseVMTags(tagsString));

                    String pool = resource.path("pool").isMissingNode()
                        ? null
                        : resource.path("pool").asText(null);

                    VMResponse vmResponse = new VMResponse(
                        resource.path("vmid").asInt(),
                        resource.path("name").asText(""),
                        resource.path("node").asText(""),
                        resource.path("status").asText(""),
                        resource.path("cpus").asInt(0),
                        resource.path("maxmem").asLong(0),
                        resource.path("maxdisk").asLong(0),
                        resource.path("uptime").asLong(0),
                        resource.path("type").asText(""),
                        new ArrayList<>(vmTags),
                        pool,
                        resource.path("template").asInt(0)
                    );
                    vms.add(vmResponse);
                }
            }

            return vms;
        } catch (Exception e) {
            LOG.error("Error listing VMs from inventory", e);
            throw ProxmoxException.internalError("list VMs", e);
        }
    }
}
