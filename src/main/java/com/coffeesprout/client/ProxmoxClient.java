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

    @GET
    @Path("/nodes/{node}/qemu")
    @Produces(MediaType.APPLICATION_JSON)
    VMsResponse getVMs(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);

    /**
     * Create a new QEMU VM on a given node.
     * The request is submitted as form data.
     */
    @POST
    @Path("/nodes/{node}/qemu")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    CreateVMResponse createVM(@PathParam("node") String node,
                              @CookieParam("PVEAuthCookie") String ticket,
                              CreateVMRequest request);

    /**
     * Import a cloud image into a specific storage.
     * This endpoint assumes that Proxmox can import content via URL.
     * (This is a conceptual endpoint—adjust as needed.)
     */
    @POST
    @Path("/nodes/{node}/storage/{storage}/content")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ImportImageResponse importCloudImage(@PathParam("node") String node,
                                         @PathParam("storage") String storage,
                                         @CookieParam("PVEAuthCookie") String ticket,
                                         ImportImageRequest request);


    // Set CPU to a specified model (e.g., "host")
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigResponse setCpu(@PathParam("node") String node,
                          @PathParam("vmid") int vmid,
                          @FormParam("cpu") String cpu);

    // Set cloud-init IP configuration
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigResponse setIpConfig(@PathParam("node") String node,
                               @PathParam("vmid") int vmid,
                               @FormParam("ipconfig0") String ipconfig0);

    // Set boot order (e.g., boot from hard disk with "c")
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigResponse setBoot(@PathParam("node") String node,
                           @PathParam("vmid") int vmid,
                           @FormParam("boot") String boot);

    /**
     * Update the scsi0 configuration. This method is used both to import a disk (by including an "import-from" parameter)
     * and to resize the disk (by setting a new size).
     */
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigResponse updateDisk(@PathParam("node") String node,
                              @PathParam("vmid") int vmid,
                              @FormParam("scsi0") String diskParam);

    // Start the VM
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/start")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse startVM(@PathParam("node") String node,
                           @PathParam("vmid") int vmid);
}

