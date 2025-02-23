package com.coffeesprout.client;


import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "proxmox-api")
@Path("/")
public interface ProxmoxClient {

    @POST
    @Path("/access/ticket")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    LoginResponse login(LoginRequest request);

    @GET
    @Path("/nodes")
    @Produces(MediaType.APPLICATION_JSON)
    NodesResponse getNodes(@CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/storage")
    @Produces(MediaType.APPLICATION_JSON)
    StorageResponse getStoragePools(@CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/nodes/{node}/status")
    @Produces(MediaType.APPLICATION_JSON)
    NodeStatusResponse getNodeStatus(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/nodes/{node}/storage")
    @Produces(MediaType.APPLICATION_JSON)
    StorageResponse getNodeStorage(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);
}

