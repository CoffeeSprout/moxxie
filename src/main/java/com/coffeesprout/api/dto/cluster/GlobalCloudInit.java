package com.coffeesprout.api.dto.cluster;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Global cloud-init settings applied to all nodes unless overridden")
public record GlobalCloudInit(
    @Schema(description = "Default SSH public keys for all nodes")
    String sshKeys,

    @Schema(description = "Default user for all nodes", example = "admin")
    String defaultUser,

    @Schema(description = "DNS nameservers for all nodes", example = "1.1.1.1,8.8.8.8")
    String nameservers,

    @Schema(description = "DNS search domain for all nodes", example = "cluster.local")
    String searchDomain,

    @Schema(description = "NTP servers for time synchronization", example = "pool.ntp.org")
    String ntpServers,

    @Schema(description = "Timezone for all nodes", example = "UTC")
    String timezone,

    @Schema(description = "Enable automatic security updates", defaultValue = "false")
    Boolean enableAutoUpdates
) {
    public GlobalCloudInit {
        if (timezone == null) {
            timezone = "UTC";
        }
        if (enableAutoUpdates == null) {
            enableAutoUpdates = false;
        }
    }
}
