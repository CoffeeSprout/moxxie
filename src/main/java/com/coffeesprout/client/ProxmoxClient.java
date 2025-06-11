package com.coffeesprout.client;


import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "proxmox-api")
@Path("/")
public interface ProxmoxClient {

    @POST
    @Path("/access/ticket")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    LoginResponse login(@FormParam("username") String username, @FormParam("password") String password);

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse getStatus();

    @GET
    @Path("/nodes")
    @Produces(MediaType.APPLICATION_JSON)
    NodesResponse getNodes(@CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/storage")
    @Produces(MediaType.APPLICATION_JSON)
    StorageResponse getStorage(@CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/nodes/{node}/status")
    @Produces(MediaType.APPLICATION_JSON)
    NodeStatusResponse getNodeStatus(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/nodes/{node}/storage")
    @Produces(MediaType.APPLICATION_JSON)
    StorageResponse getNodeStorage(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/cluster/resources")
    @Produces(MediaType.APPLICATION_JSON)
    VMsResponse getVMs(@CookieParam("PVEAuthCookie") String ticket);

    @GET
    @Path("/nodes/{node}/qemu")
    @Produces(MediaType.APPLICATION_JSON)
    VMsResponse getNodeVMs(@PathParam("node") String node, @CookieParam("PVEAuthCookie") String ticket);

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
                              @HeaderParam("CSRFPreventionToken") String csrfToken,
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
                           @PathParam("vmid") int vmid,
                           @CookieParam("PVEAuthCookie") String ticket,
                           @HeaderParam("CSRFPreventionToken") String csrfToken);

    // Stop the VM
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/stop")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse stopVM(@PathParam("node") String node,
                          @PathParam("vmid") int vmid,
                          @CookieParam("PVEAuthCookie") String ticket,
                          @HeaderParam("CSRFPreventionToken") String csrfToken);

    // Delete the VM
    @DELETE
    @Path("/nodes/{node}/qemu/{vmid}")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse deleteVM(@PathParam("node") String node,
                            @PathParam("vmid") int vmid,
                            @CookieParam("PVEAuthCookie") String ticket,
                            @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Get specific VM configuration/status
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Produces(MediaType.APPLICATION_JSON)
    VMConfigResponse getVMConfig(@PathParam("node") String node,
                                 @PathParam("vmid") int vmid,
                                 @CookieParam("PVEAuthCookie") String ticket);
    
    // Get network interfaces for a node
    @GET
    @Path("/nodes/{node}/network")
    @Produces(MediaType.APPLICATION_JSON)
    NetworkResponse getNodeNetworks(@PathParam("node") String node,
                                    @CookieParam("PVEAuthCookie") String ticket);
    
    // Reboot the VM
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/reboot")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse rebootVM(@PathParam("node") String node,
                            @PathParam("vmid") int vmid,
                            @CookieParam("PVEAuthCookie") String ticket,
                            @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Suspend the VM
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/suspend")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse suspendVM(@PathParam("node") String node,
                             @PathParam("vmid") int vmid,
                             @CookieParam("PVEAuthCookie") String ticket,
                             @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Resume the VM
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/resume")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse resumeVM(@PathParam("node") String node,
                            @PathParam("vmid") int vmid,
                            @CookieParam("PVEAuthCookie") String ticket,
                            @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Shutdown the VM (graceful shutdown via ACPI)
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/status/shutdown")
    @Produces(MediaType.APPLICATION_JSON)
    StatusResponse shutdownVM(@PathParam("node") String node,
                              @PathParam("vmid") int vmid,
                              @CookieParam("PVEAuthCookie") String ticket,
                              @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Get detailed VM status
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/status/current")
    @Produces(MediaType.APPLICATION_JSON)
    VMStatusResponse getVMStatus(@PathParam("node") String node,
                                 @PathParam("vmid") int vmid,
                                 @CookieParam("PVEAuthCookie") String ticket);
    
    // Get VM configuration with CSRF token
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode getVMConfig(@PathParam("node") String node,
                                                        @PathParam("vmid") int vmid,
                                                        @CookieParam("PVEAuthCookie") String ticket,
                                                        @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Update VM configuration (generic method for any config update)
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode updateVMConfig(@PathParam("node") String node,
                                                           @PathParam("vmid") int vmid,
                                                           @CookieParam("PVEAuthCookie") String ticket,
                                                           @HeaderParam("CSRFPreventionToken") String csrfToken,
                                                           String formData);
    
    // Get cluster resources with type filter
    @GET
    @Path("/cluster/resources")
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode getClusterResources(@CookieParam("PVEAuthCookie") String ticket,
                                                                @HeaderParam("CSRFPreventionToken") String csrfToken,
                                                                @QueryParam("type") String type);
    
    // Pool API Methods
    @GET
    @Path("/pools")
    @Produces(MediaType.APPLICATION_JSON)
    PoolsResponse listPools(@CookieParam("PVEAuthCookie") String ticket);
    
    @GET
    @Path("/pools/{poolid}")
    @Produces(MediaType.APPLICATION_JSON)
    PoolDetailResponse getPool(@PathParam("poolid") String poolId,
                               @CookieParam("PVEAuthCookie") String ticket);
    
    // SDN API Methods
    
    // List SDN zones
    @GET
    @Path("/cluster/sdn/zones")
    @Produces(MediaType.APPLICATION_JSON)
    NetworkZonesResponse listSDNZones(@CookieParam("PVEAuthCookie") String ticket);
    
    // Get specific zone details
    @GET
    @Path("/cluster/sdn/zones/{zone}")
    @Produces(MediaType.APPLICATION_JSON)
    NetworkZoneResponse getSDNZone(@PathParam("zone") String zone,
                                   @CookieParam("PVEAuthCookie") String ticket);
    
    // List VNets in a zone
    @GET
    @Path("/cluster/sdn/vnets")
    @Produces(MediaType.APPLICATION_JSON)
    VNetsResponse listVNets(@QueryParam("zone") String zone,
                            @CookieParam("PVEAuthCookie") String ticket);
    
    // Create a new VNet
    @POST
    @Path("/cluster/sdn/vnets")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    CreateVNetResponse createVNet(@FormParam("vnet") String vnetId,
                                  @FormParam("zone") String zone,
                                  @FormParam("tag") Integer vlanTag,
                                  @FormParam("alias") String alias,
                                  @CookieParam("PVEAuthCookie") String ticket,
                                  @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Delete a VNet
    @DELETE
    @Path("/cluster/sdn/vnets/{vnet}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteResponse deleteVNet(@PathParam("vnet") String vnetId,
                              @CookieParam("PVEAuthCookie") String ticket,
                              @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Apply SDN configuration
    @PUT
    @Path("/cluster/sdn")
    @Produces(MediaType.APPLICATION_JSON)
    ApplySDNResponse applySDNConfig(@CookieParam("PVEAuthCookie") String ticket,
                                    @HeaderParam("CSRFPreventionToken") String csrfToken);
}

