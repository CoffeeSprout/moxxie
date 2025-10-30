package com.coffeesprout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

/**
 * Ansible dynamic inventory response in JSON format.
 * Follows the Ansible inventory plugin specification.
 * See: https://docs.ansible.com/ansible/latest/dev_guide/developing_inventory.html
 */
@RegisterForReflection
public record AnsibleInventoryResponse(
    @JsonProperty("_meta")
    Meta meta,

    Map<String, Group> groups
) {
    /**
     * Metadata section containing hostvars for all hosts.
     */
    @RegisterForReflection
    public record Meta(
        Map<String, HostVars> hostvars
    ) {}

    /**
     * Variables for a single host.
     */
    @RegisterForReflection
    public record HostVars(
        @JsonProperty("ansible_host")
        String ansibleHost,

        Integer vmid,
        String node,
        String status,
        List<String> tags,
        Integer cores,
        Integer memory,
        String name,

        @JsonProperty("moxxie_managed")
        boolean moxxieManaged,

        Map<String, Object> custom
    ) {}

    /**
     * An inventory group containing hosts and optional children groups.
     */
    @RegisterForReflection
    public record Group(
        List<String> hosts,
        List<String> children,
        Map<String, Object> vars
    ) {}
}
