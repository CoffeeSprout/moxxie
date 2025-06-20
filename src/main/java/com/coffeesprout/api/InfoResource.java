package com.coffeesprout.api;

import com.coffeesprout.api.dto.InfoResponse;
import com.coffeesprout.config.MoxxieConfig;
import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Info", description = "Instance information endpoints")
public class InfoResource {

    @Inject
    MoxxieConfig config;
    
    @Inject
    LocationService locationService;

    @GET
    @Operation(summary = "Get instance information", description = "Returns information about this Moxxie instance")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Instance information retrieved successfully",
            content = @Content(schema = @Schema(implementation = InfoResponse.class)))
    })
    public InfoResponse getInfo() {
        LocationInfo location = locationService.getLocationInfo();
        
        return new InfoResponse(
            location.instanceId(),
            location.fullLocation(),
            config.instance().version(),
            config.proxmox().url(),
            "healthy"
        );
    }
}