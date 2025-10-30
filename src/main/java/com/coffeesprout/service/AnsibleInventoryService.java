package com.coffeesprout.service;

import com.coffeesprout.api.dto.AnsibleInventoryResponse;
import com.coffeesprout.api.dto.AnsibleInventoryResponse.Group;
import com.coffeesprout.api.dto.AnsibleInventoryResponse.HostVars;
import com.coffeesprout.api.dto.AnsibleInventoryResponse.Meta;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.util.TagUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating Ansible dynamic inventory from Moxxie-managed VMs.
 * Supports both JSON and INI inventory formats.
 */
@ApplicationScoped
@AutoAuthenticate
public class AnsibleInventoryService {

    private static final Logger LOG = Logger.getLogger(AnsibleInventoryService.class);

    @Inject
    VMService vmService;

    /**
     * Generate Ansible inventory in JSON format.
     * Groups VMs by their tags and includes all VM metadata as hostvars.
     *
     * @param tags Filter by tags (AND logic)
     * @param client Filter by client (convenience for client-<name> tag)
     * @param environment Filter by environment (convenience for env-<env> tag)
     * @param node Filter by node name
     * @param status Filter by VM status
     * @param moxxieOnly Include only Moxxie-managed VMs (with moxxie tag)
     * @param ticket Authentication ticket (auto-injected)
     * @return JSON inventory response
     */
    @SafeMode(false)  // Read operation
    public AnsibleInventoryResponse generateJSONInventory(
        List<String> tags,
        String client,
        String environment,
        String node,
        String status,
        boolean moxxieOnly,
        @AuthTicket String ticket
    ) {
        LOG.infof("Generating JSON inventory: tags=%s, client=%s, environment=%s, node=%s, status=%s, moxxieOnly=%s",
                 tags, client, environment, node, status, moxxieOnly);

        // Build tag filter list
        List<String> effectiveTags = buildEffectiveTagFilter(tags, client, environment, moxxieOnly);

        // Fetch filtered VMs
        List<VMResponse> vms = vmService.listVMsWithFilters(effectiveTags, null, node, status, ticket);

        LOG.infof("Found %d VMs matching filter criteria", vms.size());

        // Build hostvars map
        Map<String, HostVars> hostvars = new HashMap<>();
        for (VMResponse vm : vms) {
            String hostname = buildHostname(vm);
            hostvars.put(hostname, buildHostVars(vm));
        }

        // Build groups
        Map<String, Group> groups = buildGroups(vms);

        // Add special groups
        groups.put("all", new Group(
            vms.stream().map(this::buildHostname).collect(Collectors.toList()),
            null,
            Map.of()
        ));

        Meta meta = new Meta(hostvars);
        return new AnsibleInventoryResponse(meta, groups);
    }

    /**
     * Generate Ansible inventory in INI format.
     *
     * @param tags Filter by tags (AND logic)
     * @param client Filter by client
     * @param environment Filter by environment
     * @param node Filter by node name
     * @param status Filter by VM status
     * @param moxxieOnly Include only Moxxie-managed VMs
     * @param ticket Authentication ticket (auto-injected)
     * @return INI format inventory as string
     */
    @SafeMode(false)  // Read operation
    public String generateINIInventory(
        List<String> tags,
        String client,
        String environment,
        String node,
        String status,
        boolean moxxieOnly,
        @AuthTicket String ticket
    ) {
        LOG.infof("Generating INI inventory: tags=%s, client=%s, environment=%s, node=%s, status=%s, moxxieOnly=%s",
                 tags, client, environment, node, status, moxxieOnly);

        List<String> effectiveTags = buildEffectiveTagFilter(tags, client, environment, moxxieOnly);
        List<VMResponse> vms = vmService.listVMsWithFilters(effectiveTags, null, node, status, ticket);

        LOG.infof("Found %d VMs matching filter criteria", vms.size());

        StringBuilder ini = new StringBuilder();

        // Group VMs by tags
        Map<String, List<VMResponse>> vmsByGroup = groupVMsByTags(vms);

        // Write each group
        for (Map.Entry<String, List<VMResponse>> entry : vmsByGroup.entrySet()) {
            String groupName = entry.getKey();
            List<VMResponse> groupVMs = entry.getValue();

            ini.append("[").append(groupName).append("]\n");

            for (VMResponse vm : groupVMs) {
                String hostname = buildHostname(vm);
                String ansibleHost = extractPrimaryIP(vm);

                if (ansibleHost != null && !ansibleHost.isEmpty()) {
                    ini.append(hostname)
                       .append(" ansible_host=").append(ansibleHost)
                       .append(" vmid=").append(vm.vmid())
                       .append(" node=").append(vm.node())
                       .append(" status=").append(vm.status())
                       .append("\n");
                } else {
                    // No IP address available
                    ini.append(hostname)
                       .append(" vmid=").append(vm.vmid())
                       .append(" node=").append(vm.node())
                       .append(" status=").append(vm.status())
                       .append("\n");
                }
            }
            ini.append("\n");
        }

        // Add [all] group
        ini.append("[all]\n");
        for (VMResponse vm : vms) {
            ini.append(buildHostname(vm)).append("\n");
        }

        return ini.toString();
    }

    /**
     * Build effective tag filter by combining explicit tags with client/environment filters.
     */
    private List<String> buildEffectiveTagFilter(List<String> tags, String client, String environment, boolean moxxieOnly) {
        List<String> effectiveTags = new ArrayList<>();

        if (tags != null) {
            effectiveTags.addAll(tags);
        }

        if (client != null && !client.isBlank()) {
            effectiveTags.add(TagUtils.client(client));
        }

        if (environment != null && !environment.isBlank()) {
            effectiveTags.add(TagUtils.env(environment));
        }

        if (moxxieOnly) {
            effectiveTags.add("moxxie");
        }

        return effectiveTags.isEmpty() ? null : effectiveTags;
    }

    /**
     * Build hostname for Ansible inventory.
     * Uses VM name if available, otherwise vm-<vmid>.
     */
    private String buildHostname(VMResponse vm) {
        if (vm.name() != null && !vm.name().isBlank()) {
            // Sanitize name for Ansible (replace spaces and special chars with dashes)
            return vm.name().replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase();
        }
        return "vm-" + vm.vmid();
    }

    /**
     * Build host variables for a VM.
     */
    private HostVars buildHostVars(VMResponse vm) {
        String primaryIP = extractPrimaryIP(vm);
        boolean moxxieManaged = vm.tags() != null && vm.tags().contains("moxxie");

        return new HostVars(
            primaryIP,
            vm.vmid(),
            vm.node(),
            vm.status(),
            vm.tags(),
            vm.cpus(),
            (int)(vm.maxmem() / (1024 * 1024)), // Convert bytes to MB
            vm.name(),
            moxxieManaged,
            Map.of() // Custom vars - can be extended later
        );
    }

    /**
     * Extract primary IP address from VM.
     * Currently returns null - requires QEMU agent data.
     * TODO: Integrate with QEMU agent to get actual IP addresses
     */
    private String extractPrimaryIP(VMResponse vm) {
        // For now, return null
        // In future, we can query QEMU agent for IP addresses
        // via proxmoxClient.getVMAgentNetworkInfo()
        return null;
    }

    /**
     * Build inventory groups based on VM tags.
     * Each unique tag becomes a group.
     */
    private Map<String, Group> buildGroups(List<VMResponse> vms) {
        Map<String, List<String>> groupMembership = new HashMap<>();

        // Collect group memberships
        for (VMResponse vm : vms) {
            String hostname = buildHostname(vm);
            if (vm.tags() != null) {
                for (String tag : vm.tags()) {
                    // Sanitize tag for group name
                    String groupName = tag.replaceAll("[^a-zA-Z0-9_-]", "_");
                    groupMembership.computeIfAbsent(groupName, k -> new ArrayList<>()).add(hostname);
                }
            }

            // Add to default groups based on node and status
            String nodeGroup = "node_" + vm.node();
            groupMembership.computeIfAbsent(nodeGroup, k -> new ArrayList<>()).add(hostname);

            String statusGroup = "status_" + vm.status();
            groupMembership.computeIfAbsent(statusGroup, k -> new ArrayList<>()).add(hostname);
        }

        // Convert to Group objects
        Map<String, Group> groups = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : groupMembership.entrySet()) {
            groups.put(entry.getKey(), new Group(entry.getValue(), null, Map.of()));
        }

        return groups;
    }

    /**
     * Group VMs by their tags for INI format.
     */
    private Map<String, List<VMResponse>> groupVMsByTags(List<VMResponse> vms) {
        Map<String, List<VMResponse>> groups = new LinkedHashMap<>();

        // Create groups for each tag
        for (VMResponse vm : vms) {
            if (vm.tags() != null && !vm.tags().isEmpty()) {
                for (String tag : vm.tags()) {
                    String groupName = tag.replaceAll("[^a-zA-Z0-9_-]", "_");
                    groups.computeIfAbsent(groupName, k -> new ArrayList<>()).add(vm);
                }
            }

            // Node group
            String nodeGroup = "node_" + vm.node();
            groups.computeIfAbsent(nodeGroup, k -> new ArrayList<>()).add(vm);

            // Status group
            String statusGroup = "status_" + vm.status();
            groups.computeIfAbsent(statusGroup, k -> new ArrayList<>()).add(vm);
        }

        return groups;
    }
}
