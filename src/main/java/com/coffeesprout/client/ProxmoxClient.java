package com.coffeesprout.client;


import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import com.fasterxml.jackson.databind.JsonNode;

@RegisterRestClient(configKey = "proxmox-api")
@RegisterProvider(ProxmoxClientLoggingFilter.class)
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

    /**
     * Clone a VM from a template or existing VM.
     * Creates a new VM based on an existing VM or template.
     */
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/clone")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse cloneVM(@PathParam("node") String node,
                               @PathParam("vmid") int templateId,
                               @FormParam("newid") int newVmId,
                               @FormParam("name") String name,
                               @FormParam("description") String description,
                               @FormParam("full") Integer fullClone,
                               @FormParam("pool") String pool,
                               @FormParam("snapname") String snapname,
                               @FormParam("storage") String storage,
                               @FormParam("target") String targetNode,
                               @CookieParam("PVEAuthCookie") String ticket,
                               @HeaderParam("CSRFPreventionToken") String csrfToken);

    /**
     * Convert a VM to a template.
     * Once converted, the VM becomes a template and cannot be started.
     */
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/template")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode convertToTemplate(@PathParam("node") String node,
                               @PathParam("vmid") int vmid,
                               @CookieParam("PVEAuthCookie") String ticket,
                               @HeaderParam("CSRFPreventionToken") String csrfToken);

    /**
     * Resize a VM disk.
     */
    @PUT
    @Path("/nodes/{node}/qemu/{vmid}/resize")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigResponse resizeDisk(@PathParam("node") String node,
                              @PathParam("vmid") int vmid,
                              @FormParam("disk") String disk,
                              @FormParam("size") String size,
                              @CookieParam("PVEAuthCookie") String ticket,
                              @HeaderParam("CSRFPreventionToken") String csrfToken);

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
                              @FormParam("scsi0") String diskParam,
                              @CookieParam("PVEAuthCookie") String ticket,
                              @HeaderParam("CSRFPreventionToken") String csrfToken);

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
    
    // Snapshot Management
    
    // List all snapshots for a VM
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/snapshot")
    @Produces(MediaType.APPLICATION_JSON)
    SnapshotsResponse listSnapshots(@PathParam("node") String node,
                                    @PathParam("vmid") int vmid,
                                    @CookieParam("PVEAuthCookie") String ticket);
    
    // Create a new snapshot
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/snapshot")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse createSnapshot(@PathParam("node") String node,
                                      @PathParam("vmid") int vmid,
                                      @FormParam("snapname") String snapname,
                                      @FormParam("description") String description,
                                      @FormParam("vmstate") Integer vmstate,
                                      @CookieParam("PVEAuthCookie") String ticket,
                                      @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Get snapshot configuration
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/snapshot/{snapname}/config")
    @Produces(MediaType.APPLICATION_JSON)
    VMConfigResponse getSnapshotConfig(@PathParam("node") String node,
                                       @PathParam("vmid") int vmid,
                                       @PathParam("snapname") String snapname,
                                       @CookieParam("PVEAuthCookie") String ticket);
    
    // Delete a snapshot
    @DELETE
    @Path("/nodes/{node}/qemu/{vmid}/snapshot/{snapname}")
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse deleteSnapshot(@PathParam("node") String node,
                                      @PathParam("vmid") int vmid,
                                      @PathParam("snapname") String snapname,
                                      @CookieParam("PVEAuthCookie") String ticket,
                                      @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Rollback to a snapshot
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/snapshot/{snapname}/rollback")
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse rollbackSnapshot(@PathParam("node") String node,
                                        @PathParam("vmid") int vmid,
                                        @PathParam("snapname") String snapname,
                                        @CookieParam("PVEAuthCookie") String ticket,
                                        @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Backup Management
    
    // Create a backup using vzdump
    @POST
    @Path("/nodes/{node}/vzdump")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse createBackup(@PathParam("node") String node,
                                    @FormParam("vmid") String vmid,
                                    @FormParam("storage") String storage,
                                    @FormParam("mode") String mode,
                                    @FormParam("compress") String compress,
                                    @FormParam("notes-template") String notes,
                                    @FormParam("protected") Integer protectedFlag,
                                    @FormParam("remove") Integer removeOlder,
                                    @FormParam("mailnotification") String mailNotification,
                                    @CookieParam("PVEAuthCookie") String ticket,
                                    @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // List storage content (including backups)
    @GET
    @Path("/nodes/{node}/storage/{storage}/content")
    @Produces(MediaType.APPLICATION_JSON)
    StorageContentResponse listStorageContent(@PathParam("node") String node,
                                              @PathParam("storage") String storage,
                                              @QueryParam("content") String content,
                                              @QueryParam("vmid") Integer vmid,
                                              @CookieParam("PVEAuthCookie") String ticket);
    
    // Delete a backup
    @DELETE
    @Path("/nodes/{node}/storage/{storage}/content/{volume}")
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse deleteBackup(@PathParam("node") String node,
                                    @PathParam("storage") String storage,
                                    @PathParam("volume") String volume,
                                    @CookieParam("PVEAuthCookie") String ticket,
                                    @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Restore VM from backup (uses regular VM creation with archive parameter)
    @POST
    @Path("/nodes/{node}/qemu")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse restoreVM(@PathParam("node") String node,
                                 @FormParam("vmid") Integer vmid,
                                 @FormParam("archive") String archive,
                                 @FormParam("storage") String storage,
                                 @FormParam("name") String name,
                                 @FormParam("description") String description,
                                 @FormParam("start") Integer start,
                                 @FormParam("unique") Integer unique,
                                 @FormParam("bwlimit") Integer bandwidth,
                                 @FormParam("force") Integer force,
                                 @CookieParam("PVEAuthCookie") String ticket,
                                 @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Upload file to storage (ISO, etc)
    @POST
    @Path("/nodes/{node}/storage/{storage}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse uploadToStorage(@PathParam("node") String node,
                                       @PathParam("storage") String storage,
                                       @FormParam("content") String content,
                                       @FormParam("filename") java.io.InputStream file,
                                       @CookieParam("PVEAuthCookie") String ticket,
                                       @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Download file from URL to storage
    @POST
    @Path("/nodes/{node}/storage/{storage}/download-url")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse downloadUrlToStorage(@PathParam("node") String node,
                                            @PathParam("storage") String storage,
                                            @FormParam("url") String url,
                                            @FormParam("content") String content,
                                            @FormParam("filename") String filename,
                                            @FormParam("checksum") String checksum,
                                            @FormParam("checksum-algorithm") String checksumAlgorithm,
                                            @FormParam("verify-certificates") Integer verifyCertificates,
                                            @CookieParam("PVEAuthCookie") String ticket,
                                            @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Get storage status
    @GET
    @Path("/nodes/{node}/storage/{storage}/status")
    @Produces(MediaType.APPLICATION_JSON)
    StorageStatusResponse getStorageStatus(@PathParam("node") String node,
                                           @PathParam("storage") String storage,
                                           @CookieParam("PVEAuthCookie") String ticket);
    
    // Task Management
    
    // Get task status
    @GET
    @Path("/nodes/{node}/tasks/{upid}/status")
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode getTaskStatus(@PathParam("node") String node,
                                                          @PathParam("upid") String upid,
                                                          @CookieParam("PVEAuthCookie") String ticket);
    
    // Get task log
    @GET
    @Path("/nodes/{node}/tasks/{upid}/log")
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode getTaskLog(@PathParam("node") String node,
                                                       @PathParam("upid") String upid,
                                                       @QueryParam("start") Integer start,
                                                       @QueryParam("limit") Integer limit,
                                                       @CookieParam("PVEAuthCookie") String ticket);
    
    // List tasks on a node
    @GET
    @Path("/nodes/{node}/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    TaskListData getNodeTasks(@PathParam("node") String node,
                              @QueryParam("start") Integer start,
                              @QueryParam("limit") Integer limit,
                              @QueryParam("statusfilter") String statusFilter,
                              @QueryParam("typefilter") String typeFilter,
                              @QueryParam("userfilter") String userFilter,
                              @QueryParam("vmid") Integer vmid,
                              @CookieParam("PVEAuthCookie") String ticket);
    
    // Stop a task
    @DELETE
    @Path("/nodes/{node}/tasks/{upid}")
    @Produces(MediaType.APPLICATION_JSON)
    com.fasterxml.jackson.databind.JsonNode stopTask(@PathParam("node") String node,
                                                     @PathParam("upid") String upid,
                                                     @CookieParam("PVEAuthCookie") String ticket,
                                                     @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Console Access APIs
    
    // VNC proxy
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/vncproxy")
    @Produces(MediaType.APPLICATION_JSON)
    ProxmoxConsoleResponse createVNCProxy(@PathParam("node") String node,
                                          @PathParam("vmid") int vmid,
                                          @CookieParam("PVEAuthCookie") String ticket,
                                          @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // VNC websocket
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/vncwebsocket")
    @Produces(MediaType.APPLICATION_JSON)
    ProxmoxConsoleResponse getVNCWebSocket(@PathParam("node") String node,
                                           @PathParam("vmid") int vmid,
                                           @QueryParam("vncticket") String vncticket,
                                           @QueryParam("port") String port,
                                           @CookieParam("PVEAuthCookie") String ticket);
    
    // SPICE proxy
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/spiceproxy")
    @Produces(MediaType.APPLICATION_JSON)
    ProxmoxConsoleResponse createSPICEProxy(@PathParam("node") String node,
                                            @PathParam("vmid") int vmid,
                                            @CookieParam("PVEAuthCookie") String ticket,
                                            @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Terminal proxy (for serial console)
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/termproxy")
    @Produces(MediaType.APPLICATION_JSON)
    ProxmoxConsoleResponse createTermProxy(@PathParam("node") String node,
                                           @PathParam("vmid") int vmid,
                                           @CookieParam("PVEAuthCookie") String ticket,
                                           @HeaderParam("CSRFPreventionToken") String csrfToken);
    
    // Backup Job Management
    
    // List all backup jobs
    @GET
    @Path("/cluster/backup")
    @Produces(MediaType.APPLICATION_JSON)
    BackupJobsResponse listBackupJobs(@CookieParam("PVEAuthCookie") String ticket);
    
    // Get specific backup job
    @GET
    @Path("/cluster/backup/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    BackupJobDetailResponse getBackupJob(@PathParam("id") String id,
                                         @CookieParam("PVEAuthCookie") String ticket);
    
    // VM Migration
    
    // Check migration preconditions
    @GET
    @Path("/nodes/{node}/qemu/{vmid}/migrate")
    @Produces(MediaType.APPLICATION_JSON)
    MigrationPreconditionsResponse checkMigrationPreconditions(@PathParam("node") String node,
                                                              @PathParam("vmid") int vmid,
                                                              @QueryParam("target") String targetNode,
                                                              @CookieParam("PVEAuthCookie") String ticket);
    
    // Execute VM migration
    @POST
    @Path("/nodes/{node}/qemu/{vmid}/migrate")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TaskStatusResponse migrateVM(@PathParam("node") String node,
                                 @PathParam("vmid") int vmid,
                                 @FormParam("target") String targetNode,
                                 @FormParam("online") Integer online,
                                 @FormParam("with-local-disks") Integer withLocalDisks,
                                 @FormParam("force") Integer force,
                                 @FormParam("bwlimit") Integer bwlimit,
                                 @FormParam("targetstorage") String targetStorage,
                                 @FormParam("migration_type") String migrationType,
                                 @FormParam("migration_network") String migrationNetwork,
                                 @CookieParam("PVEAuthCookie") String ticket,
                                 @HeaderParam("CSRFPreventionToken") String csrfToken);
}

