package com.coffeesprout.scheduler.model;

import jakarta.validation.constraints.NotNull;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "VM selector configuration for identifying target VMs")
public record VMSelector(
    @Schema(description = "Type of selector", required = true,
            example = "VM_IDS",
            enumeration = {"ALL", "VM_IDS", "NAME_PATTERN", "TAG_EXPRESSION"})
    @NotNull(message = "Selector type is required") SelectorType type,

    @Schema(description = "Value for the selector. Format depends on type:\n" +
            "- ALL: Use '*' or leave empty\n" +
            "- VM_IDS: Comma-separated VM IDs (e.g., '8200,8201,8202')\n" +
            "- NAME_PATTERN: Wildcard pattern (e.g., 'web-*', '*-prod')\n" +
            "- TAG_EXPRESSION: Tag expression (e.g., 'env-prod AND client-acme')",
            required = true,
            example = "8200,8201,8202")
    @NotNull(message = "Selector value is required") String value
) {
    public enum SelectorType {
        ALL,           // Select all VMs
        VM_IDS,        // Select specific VM IDs
        NAME_PATTERN,  // Select by name pattern with wildcards
        TAG_EXPRESSION // Select by tag expression
    }
}
