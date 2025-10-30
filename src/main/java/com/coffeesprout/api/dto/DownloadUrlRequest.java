package com.coffeesprout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for downloading content from a URL to storage
 */
@Schema(description = "Request to download content from a URL")
public record DownloadUrlRequest(
    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    @Schema(description = "Source URL to download from",
            example = "https://releases.ubuntu.com/22.04.3/ubuntu-22.04.3-live-server-amd64.iso",
            required = true)
    String url,

    @NotBlank(message = "Filename is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Filename can only contain alphanumeric characters, dots, dashes and underscores")
    @Schema(description = "Target filename in storage",
            example = "ubuntu-22.04.3-server.iso",
            required = true)
    String filename,

    @JsonProperty("verify_certificate")
    @Schema(description = "Whether to verify SSL certificate",
            defaultValue = "true")
    Boolean verifyCertificate,

    @JsonProperty("checksum_url")
    @Schema(description = "URL containing checksums (e.g., SHA256SUMS file)",
            example = "https://releases.ubuntu.com/22.04.3/SHA256SUMS")
    String checksumUrl,

    @Schema(description = "Expected checksum value (if not using checksum URL)",
            example = "a4acfda10b18da50e2ec50ccaf860d7f20b389df8765611142305c0e911d16fd")
    String checksum,

    @JsonProperty("checksum_algorithm")
    @Schema(description = "Checksum algorithm",
            enumeration = {"md5", "sha1", "sha224", "sha256", "sha384", "sha512"},
            defaultValue = "sha256")
    String checksumAlgorithm
) {

    public DownloadUrlRequest {
        // Set defaults
        if (verifyCertificate == null) {
            verifyCertificate = true;
        }
        if (checksumAlgorithm == null && (checksum != null || checksumUrl != null)) {
            checksumAlgorithm = "sha256";
        }
    }
}
