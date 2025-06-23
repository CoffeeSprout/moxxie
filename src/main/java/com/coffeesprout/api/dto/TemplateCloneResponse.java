package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for template clone operations
 */
@Schema(description = "Response from cloning a VM from a template")
public record TemplateCloneResponse(
    @Schema(description = "The ID of the newly created VM", example = "100")
    int vmId,
    
    @Schema(description = "The name of the newly created VM", example = "k8s-control-01")
    String name,
    
    @Schema(description = "The node where the VM is currently located", example = "hv7")
    String node,
    
    @Schema(description = "Status message", example = "VM created successfully from template 9001")
    String message
) {}