package com.coffeesprout.api.dto;

import com.coffeesprout.api.dto.cluster.NodeTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for CloudInitVMRequest to avoid long parameter lists
 */
public class CloudInitVMRequestBuilder {
    // Required fields
    private Integer vmid;
    private String name;
    private String node;
    private Integer cores;
    private Integer memoryMB;
    private String imageSource;
    private String targetStorage;
    
    // Optional fields with defaults
    private String templateNode;
    private Integer diskSizeGB = 50;
    private String cloudInitUser;
    private String cloudInitPassword;
    private String sshKeys;
    private List<NetworkConfig> networks = new ArrayList<>();
    private List<String> ipConfigs = new ArrayList<>();
    private CloudInitVMRequest.NetworkConfig network; // deprecated
    private String ipConfig; // deprecated
    private String searchDomain;
    private String nameservers;
    private String cpuType = "x86-64-v2-AES";
    private Boolean qemuAgent = true;
    private Boolean start = false;
    private String description;
    private String tags;
    private CloudInitVMRequest.DiskOptions diskOptions;
    
    // Firmware and hardware configuration
    private FirmwareConfig firmware;
    private String scsihw = "virtio-scsi-pci";
    private String serial0;
    private String vgaType = "std";
    
    // Private constructor - use static factory methods
    private CloudInitVMRequestBuilder() {}
    
    /**
     * Create a new builder
     */
    public static CloudInitVMRequestBuilder builder() {
        return new CloudInitVMRequestBuilder();
    }
    
    /**
     * Create a builder for FCOS (Fedora CoreOS) VMs that don't use cloud-init
     */
    public static CloudInitVMRequestBuilder forFCOS(Integer vmid, String name, String targetHost, NodeTemplate template) {
        return builder()
            .vmid(vmid)
            .name(name)
            .node(targetHost)
            .templateNode("storage01")
            .cores(template.cores())
            .memoryMB(template.memoryMB())
            .imageSource(template.imageSource())
            .targetStorage(template.disks().get(0).storage())
            .diskSizeGB(template.disks().get(0).sizeGB())
            .cpuType(template.cpuType())
            .qemuAgent(template.qemuAgent())
            .start(false)  // Never auto-start FCOS nodes
            .skipCloudInit();
    }
    
    /**
     * Create a builder for regular cloud-init VMs
     */
    public static CloudInitVMRequestBuilder forCloudInit(Integer vmid, String name, String targetHost, NodeTemplate template) {
        CloudInitVMRequestBuilder builder = builder()
            .vmid(vmid)
            .name(name)
            .node(targetHost)
            .templateNode("storage01")
            .cores(template.cores())
            .memoryMB(template.memoryMB())
            .imageSource(template.imageSource())
            .targetStorage(template.disks().get(0).storage())
            .diskSizeGB(template.disks().get(0).sizeGB())
            .cpuType(template.cpuType())
            .qemuAgent(template.qemuAgent());
        
        // Add cloud-init config if present
        if (template.cloudInit() != null) {
            builder.cloudInitUser(template.cloudInit().user())
                   .cloudInitPassword(template.cloudInit().password())
                   .sshKeys(template.cloudInit().sshKeys())
                   .searchDomain(template.cloudInit().searchDomain())
                   .nameservers(template.cloudInit().nameservers());
        }
        
        return builder;
    }
    
    // Builder methods for required fields
    public CloudInitVMRequestBuilder vmid(Integer vmid) {
        this.vmid = vmid;
        return this;
    }
    
    public CloudInitVMRequestBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public CloudInitVMRequestBuilder node(String node) {
        this.node = node;
        return this;
    }
    
    public CloudInitVMRequestBuilder cores(Integer cores) {
        this.cores = cores;
        return this;
    }
    
    public CloudInitVMRequestBuilder memoryMB(Integer memoryMB) {
        this.memoryMB = memoryMB;
        return this;
    }
    
    public CloudInitVMRequestBuilder imageSource(String imageSource) {
        this.imageSource = imageSource;
        return this;
    }
    
    public CloudInitVMRequestBuilder targetStorage(String targetStorage) {
        this.targetStorage = targetStorage;
        return this;
    }
    
    // Builder methods for optional fields
    public CloudInitVMRequestBuilder templateNode(String templateNode) {
        this.templateNode = templateNode;
        return this;
    }
    
    public CloudInitVMRequestBuilder diskSizeGB(Integer diskSizeGB) {
        this.diskSizeGB = diskSizeGB;
        return this;
    }
    
    public CloudInitVMRequestBuilder cloudInitUser(String cloudInitUser) {
        this.cloudInitUser = cloudInitUser;
        return this;
    }
    
    public CloudInitVMRequestBuilder cloudInitPassword(String cloudInitPassword) {
        this.cloudInitPassword = cloudInitPassword;
        return this;
    }
    
    public CloudInitVMRequestBuilder sshKeys(String sshKeys) {
        this.sshKeys = sshKeys;
        return this;
    }
    
    public CloudInitVMRequestBuilder networks(List<NetworkConfig> networks) {
        this.networks = networks != null ? networks : new ArrayList<>();
        return this;
    }
    
    public CloudInitVMRequestBuilder addNetwork(NetworkConfig network) {
        this.networks.add(network);
        return this;
    }
    
    public CloudInitVMRequestBuilder ipConfigs(List<String> ipConfigs) {
        this.ipConfigs = ipConfigs != null ? ipConfigs : new ArrayList<>();
        return this;
    }
    
    public CloudInitVMRequestBuilder addIpConfig(String ipConfig) {
        this.ipConfigs.add(ipConfig);
        return this;
    }
    
    public CloudInitVMRequestBuilder searchDomain(String searchDomain) {
        this.searchDomain = searchDomain;
        return this;
    }
    
    public CloudInitVMRequestBuilder nameservers(String nameservers) {
        this.nameservers = nameservers;
        return this;
    }
    
    public CloudInitVMRequestBuilder cpuType(String cpuType) {
        this.cpuType = cpuType;
        return this;
    }
    
    public CloudInitVMRequestBuilder qemuAgent(Boolean qemuAgent) {
        this.qemuAgent = qemuAgent;
        return this;
    }
    
    public CloudInitVMRequestBuilder start(Boolean start) {
        this.start = start;
        return this;
    }
    
    public CloudInitVMRequestBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    public CloudInitVMRequestBuilder tags(String tags) {
        this.tags = tags;
        return this;
    }
    
    public CloudInitVMRequestBuilder diskOptions(CloudInitVMRequest.DiskOptions diskOptions) {
        this.diskOptions = diskOptions;
        return this;
    }
    
    // Firmware and hardware configuration methods
    public CloudInitVMRequestBuilder firmware(FirmwareConfig firmware) {
        this.firmware = firmware;
        return this;
    }
    
    public CloudInitVMRequestBuilder scsihw(String scsihw) {
        this.scsihw = scsihw;
        return this;
    }
    
    public CloudInitVMRequestBuilder serial0(String serial0) {
        this.serial0 = serial0;
        return this;
    }
    
    public CloudInitVMRequestBuilder vgaType(String vgaType) {
        this.vgaType = vgaType;
        return this;
    }
    
    /**
     * Convenience method to skip cloud-init configuration (for FCOS)
     */
    public CloudInitVMRequestBuilder skipCloudInit() {
        this.cloudInitUser = null;
        this.cloudInitPassword = null;
        this.sshKeys = null;
        this.searchDomain = null;
        this.nameservers = null;
        return this;
    }
    
    /**
     * Build the CloudInitVMRequest
     */
    public CloudInitVMRequest build() {
        // Validate required fields
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("VM name is required");
        }
        if (node == null || node.isBlank()) {
            throw new IllegalStateException("Node is required");
        }
        if (cores == null) {
            throw new IllegalStateException("CPU cores is required");
        }
        if (memoryMB == null) {
            throw new IllegalStateException("Memory is required");
        }
        if (imageSource == null || imageSource.isBlank()) {
            throw new IllegalStateException("Image source is required");
        }
        if (targetStorage == null || targetStorage.isBlank()) {
            throw new IllegalStateException("Target storage is required");
        }
        
        return new CloudInitVMRequest(
            vmid,
            name,
            node,
            templateNode,
            cores,
            memoryMB,
            imageSource,
            targetStorage,
            diskSizeGB,
            cloudInitUser,
            cloudInitPassword,
            sshKeys,
            networks.isEmpty() ? null : networks,
            ipConfigs.isEmpty() ? null : ipConfigs,
            network,
            ipConfig,
            searchDomain,
            nameservers,
            cpuType,
            qemuAgent,
            start,
            description,
            tags,
            diskOptions,
            firmware,
            scsihw,
            serial0,
            vgaType
        );
    }
}