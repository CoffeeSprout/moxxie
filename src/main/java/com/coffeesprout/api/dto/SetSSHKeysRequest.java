package com.coffeesprout.api.dto;

import jakarta.validation.constraints.*;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for setting SSH keys on an existing VM
 */
@Schema(description = "Request to set SSH keys on a VM")
public record SetSSHKeysRequest(
    @Schema(description = "SSH public keys (one per line)", required = true,
            example = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGLmQqfp8X5DUVxLruBsCmJ7m4mDGcr5V7e2BXMkNPDp test@example.com")
    @NotBlank(message = "SSH keys are required")
    String sshKeys
) {}
