package com.coffeesprout.service;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.VMResponse;

/**
 * Mediator providing consolidated access to VM inventory data and tag lookups.
 */
@ApplicationScoped
@AutoAuthenticate
public class VMTagLookupService {

    @Inject
    VMInventoryService vmInventoryService;

    @Inject
    TagService tagService;

    public List<VMResponse> listVMs(@AuthTicket String ticket) {
        return vmInventoryService.listAll(ticket);
    }

    public Set<String> getVMTags(int vmId, @AuthTicket String ticket) {
        return tagService.getVMTags(vmId, ticket);
    }

    public List<Integer> getVMsByTag(String tag, @AuthTicket String ticket) {
        return tagService.getVMsByTag(tag, ticket);
    }
}
