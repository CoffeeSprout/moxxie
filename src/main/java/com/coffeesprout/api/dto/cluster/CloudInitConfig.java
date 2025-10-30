package com.coffeesprout.api.dto.cluster;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Cloud-init configuration for nodes")
public record CloudInitConfig(
    @Schema(description = "Default user", example = "ubuntu")
    String user,

    @Schema(description = "User password (will be hashed)")
    String password,

    @Schema(description = "SSH public keys", example = "ssh-ed25519 AAAAC3...")
    String sshKeys,

    @Schema(description = "Hostname pattern with placeholders", example = "{cluster}-{role}-{index}")
    String hostnamePattern,

    @Schema(description = "DNS search domains", example = "cluster.local")
    String searchDomain,

    @Schema(description = "DNS nameservers (comma-separated)", example = "8.8.8.8,8.8.4.4")
    String nameservers,

    @Schema(description = "IP configuration patterns for each network interface",
            example = "[\"ip=10.0.1.{10+index}/24,gw=10.0.1.1\", \"ip=dhcp\"]")
    List<String> ipConfigPatterns,

    @Schema(description = "Custom user-data script (base64 encoded)")
    String userData,

    @Schema(description = "Custom meta-data (YAML format)")
    String metaData,

    @Schema(description = "Network configuration (YAML format)")
    String networkConfig,

    @Schema(description = "Packages to install")
    List<String> packages,

    @Schema(description = "Commands to run on first boot")
    List<String> runCmd,

    @Schema(description = "Additional cloud-init modules configuration")
    Map<String, Object> modules
) {
    public CloudInitConfig {
        if (modules == null) {
            modules = Map.of();
        }
    }

    public String generateHostname(String clusterName, String role, int index) {
        if (hostnamePattern == null) {
            return String.format("%s-%s-%02d", clusterName, role, index);
        }
        return hostnamePattern
            .replace("{cluster}", clusterName)
            .replace("{role}", role)
            .replace("{index}", String.valueOf(index))
            .replace("{index2}", String.format("%02d", index));
    }

    public String generateIpConfig(int networkIndex, int nodeIndex) {
        if (ipConfigPatterns == null || ipConfigPatterns.size() <= networkIndex) {
            return "ip=dhcp";
        }

        String pattern = ipConfigPatterns.get(networkIndex);
        // Replace {N+index} patterns with actual values
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{(\\d+)\\+index\\}");
        Matcher m = p.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String baseStr = m.group(1);
            int base = Integer.parseInt(baseStr);
            m.appendReplacement(sb, String.valueOf(base + nodeIndex));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
