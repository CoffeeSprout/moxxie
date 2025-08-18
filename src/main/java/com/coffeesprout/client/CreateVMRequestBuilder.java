package com.coffeesprout.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder for CreateVMRequest to avoid error-prone constructor calls with 30+ parameters.
 * Provides fluent API for VM creation with sensible defaults and common patterns.
 */
public class CreateVMRequestBuilder {
    
    private int vmid;
    private String name;
    private int cores = 2;
    private int memory = 2048;
    
    private final List<NetworkInterface> networks = new ArrayList<>();
    private final List<DiskConfig> disks = new ArrayList<>();
    private final List<IpConfig> ipConfigs = new ArrayList<>();
    
    private String scsihw = "virtio-scsi-pci";
    private String ide2;
    private String boot = "c";
    private int onboot = 0;
    private String pool;
    private String tags;
    private String agent = "1";
    private String cpu = "x86-64-v2-AES";
    private String serial0;
    private String vga;
    
    private String ciuser;
    private String cipassword;
    private String nameserver;
    private String searchdomain;
    private String sshkeys;
    private String description;
    
    // Firmware configuration
    private String machine = "pc";
    private String bios = "seabios";
    private String efidisk0;
    
    private CreateVMRequestBuilder() {}
    
    /**
     * Create a new builder instance
     */
    public static CreateVMRequestBuilder builder() {
        return new CreateVMRequestBuilder();
    }
    
    /**
     * Factory method for standard cloud-init VM
     */
    public static CreateVMRequestBuilder forCloudInit(int vmid, String name, int cores, int memory) {
        return builder()
            .vmid(vmid)
            .name(name)
            .cores(cores)
            .memory(memory)
            .agent(true)
            .serial0("socket")
            .vga("serial0")
            .ide2("local-lvm:cloudinit");
    }
    
    /**
     * Factory method for FCOS/Talos VM (no cloud-init)
     */
    public static CreateVMRequestBuilder forFCOS(int vmid, String name, int cores, int memory) {
        return builder()
            .vmid(vmid)
            .name(name)
            .cores(cores)
            .memory(memory)
            .vga("std")
            .serial0("socket");
    }
    
    /**
     * Factory method for Windows VM
     */
    public static CreateVMRequestBuilder forWindows(int vmid, String name, int cores, int memory) {
        return builder()
            .vmid(vmid)
            .name(name)
            .cores(cores)
            .memory(memory)
            .cpu("host")
            .agent(true)
            .vga("qxl");
    }
    
    // Required fields
    public CreateVMRequestBuilder vmid(int vmid) {
        this.vmid = vmid;
        return this;
    }
    
    public CreateVMRequestBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public CreateVMRequestBuilder cores(int cores) {
        this.cores = cores;
        return this;
    }
    
    public CreateVMRequestBuilder memory(int memoryMB) {
        this.memory = memoryMB;
        return this;
    }
    
    // Network configuration
    public CreateVMRequestBuilder addNetwork(String model, String bridge) {
        return addNetwork(model, bridge, null, null);
    }
    
    public CreateVMRequestBuilder addNetwork(String model, String bridge, Integer vlan, String tag) {
        this.networks.add(new NetworkInterface(model, bridge, vlan, tag));
        return this;
    }
    
    public CreateVMRequestBuilder addNetworkVirtio(String bridge) {
        return addNetwork("virtio", bridge);
    }
    
    public CreateVMRequestBuilder addNetworkVirtio(String bridge, int vlan) {
        return addNetwork("virtio", bridge, vlan, null);
    }
    
    // IP configuration for cloud-init
    public CreateVMRequestBuilder addIpConfig(String config) {
        this.ipConfigs.add(new IpConfig(config));
        return this;
    }
    
    public CreateVMRequestBuilder addStaticIp(String ip, String gateway) {
        return addIpConfig(String.format("ip=%s,gw=%s", ip, gateway));
    }
    
    public CreateVMRequestBuilder addDhcpIp() {
        return addIpConfig("ip=dhcp");
    }
    
    // Disk configuration
    public CreateVMRequestBuilder addDisk(String storage, int sizeGB) {
        this.disks.add(new DiskConfig(storage, sizeGB));
        return this;
    }
    
    // Hardware configuration
    public CreateVMRequestBuilder scsihw(String scsihw) {
        this.scsihw = scsihw;
        return this;
    }
    
    public CreateVMRequestBuilder cpu(String cpu) {
        this.cpu = cpu;
        return this;
    }
    
    public CreateVMRequestBuilder vga(String vga) {
        this.vga = vga;
        return this;
    }
    
    public CreateVMRequestBuilder serial0(String serial0) {
        this.serial0 = serial0;
        return this;
    }
    
    public CreateVMRequestBuilder boot(String boot) {
        this.boot = boot;
        return this;
    }
    
    public CreateVMRequestBuilder onboot(boolean onboot) {
        this.onboot = onboot ? 1 : 0;
        return this;
    }
    
    public CreateVMRequestBuilder agent(boolean enabled) {
        this.agent = enabled ? "1" : "0";
        return this;
    }
    
    // Cloud-init configuration
    public CreateVMRequestBuilder cloudInit(String storage) {
        this.ide2 = storage + ":cloudinit";
        return this;
    }
    
    public CreateVMRequestBuilder ide2(String ide2) {
        this.ide2 = ide2;
        return this;
    }
    
    public CreateVMRequestBuilder ciuser(String ciuser) {
        this.ciuser = ciuser;
        return this;
    }
    
    public CreateVMRequestBuilder cipassword(String cipassword) {
        this.cipassword = cipassword;
        return this;
    }
    
    public CreateVMRequestBuilder nameserver(String nameserver) {
        this.nameserver = nameserver;
        return this;
    }
    
    public CreateVMRequestBuilder nameservers(String... nameservers) {
        this.nameserver = String.join(" ", nameservers);
        return this;
    }
    
    public CreateVMRequestBuilder searchdomain(String searchdomain) {
        this.searchdomain = searchdomain;
        return this;
    }
    
    public CreateVMRequestBuilder sshkeys(String sshkeys) {
        this.sshkeys = sshkeys;
        return this;
    }
    
    // Metadata
    public CreateVMRequestBuilder pool(String pool) {
        this.pool = pool;
        return this;
    }
    
    public CreateVMRequestBuilder tags(String tags) {
        this.tags = tags;
        return this;
    }
    
    public CreateVMRequestBuilder addTag(String tag) {
        if (this.tags == null || this.tags.isEmpty()) {
            this.tags = tag;
        } else {
            this.tags = this.tags + "," + tag;
        }
        return this;
    }
    
    public CreateVMRequestBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    // Firmware configuration
    public CreateVMRequestBuilder machine(String machine) {
        this.machine = machine;
        return this;
    }
    
    public CreateVMRequestBuilder bios(String bios) {
        this.bios = bios;
        return this;
    }
    
    public CreateVMRequestBuilder efidisk0(String efidisk0) {
        this.efidisk0 = efidisk0;
        return this;
    }
    
    /**
     * Build the CreateVMRequest with validation
     */
    public CreateVMRequest build() {
        // Validation
        Objects.requireNonNull(name, "VM name is required");
        if (vmid <= 0) {
            throw new IllegalStateException("VM ID must be positive");
        }
        if (cores <= 0) {
            throw new IllegalStateException("Cores must be positive");
        }
        if (memory <= 0) {
            throw new IllegalStateException("Memory must be positive");
        }
        
        CreateVMRequest request = new CreateVMRequest();
        
        // Set required fields
        request.setVmid(vmid);
        request.setName(name);
        request.setCores(cores);
        request.setMemory(memory);
        
        // Set networks (up to 8)
        for (int i = 0; i < Math.min(networks.size(), 8); i++) {
            NetworkInterface net = networks.get(i);
            String netConfig = buildNetworkString(net);
            switch (i) {
                case 0: request.setNet0(netConfig); break;
                case 1: request.setNet1(netConfig); break;
                case 2: request.setNet2(netConfig); break;
                case 3: request.setNet3(netConfig); break;
                case 4: request.setNet4(netConfig); break;
                case 5: request.setNet5(netConfig); break;
                case 6: request.setNet6(netConfig); break;
                case 7: request.setNet7(netConfig); break;
            }
        }
        
        // Set IP configs (up to 8)
        for (int i = 0; i < Math.min(ipConfigs.size(), 8); i++) {
            String config = ipConfigs.get(i).config;
            switch (i) {
                case 0: request.setIpconfig0(config); break;
                case 1: request.setIpconfig1(config); break;
                case 2: request.setIpconfig2(config); break;
                case 3: request.setIpconfig3(config); break;
                case 4: request.setIpconfig4(config); break;
                case 5: request.setIpconfig5(config); break;
                case 6: request.setIpconfig6(config); break;
                case 7: request.setIpconfig7(config); break;
            }
        }
        
        // Set disks (up to 6 SCSI)
        for (int i = 0; i < Math.min(disks.size(), 6); i++) {
            DiskConfig disk = disks.get(i);
            String diskConfig = String.format("%s:%d", disk.storage, disk.sizeGB);
            switch (i) {
                case 0: request.setScsi0(diskConfig); break;
                case 1: request.setScsi1(diskConfig); break;
                case 2: request.setScsi2(diskConfig); break;
                case 3: request.setScsi3(diskConfig); break;
                case 4: request.setScsi4(diskConfig); break;
                case 5: request.setScsi5(diskConfig); break;
            }
        }
        
        // Set hardware config
        request.setScsihw(scsihw);
        request.setCpu(cpu);
        request.setBoot(boot);
        request.setOnboot(onboot);
        request.setAgent(agent);
        
        if (vga != null) request.setVga(vga);
        if (serial0 != null) request.setSerial0(serial0);
        
        // Set firmware config
        if (machine != null) request.setMachine(machine);
        if (bios != null) request.setBios(bios);
        if (efidisk0 != null) request.setEfidisk0(efidisk0);
        
        // Set cloud-init config
        if (ide2 != null) request.setIde2(ide2);
        if (ciuser != null) request.setCiuser(ciuser);
        if (cipassword != null) request.setCipassword(cipassword);
        if (nameserver != null) request.setNameserver(nameserver);
        if (searchdomain != null) request.setSearchdomain(searchdomain);
        if (sshkeys != null) request.setSshkeys(sshkeys);
        
        // Set metadata
        if (pool != null) request.setPool(pool);
        if (tags != null) request.setTags(tags);
        if (description != null) request.setDescription(description);
        
        return request;
    }
    
    private String buildNetworkString(NetworkInterface net) {
        StringBuilder sb = new StringBuilder();
        sb.append(net.model).append(",bridge=").append(net.bridge);
        if (net.vlan != null) {
            sb.append(",tag=").append(net.vlan);
        }
        if (net.tag != null) {
            sb.append(",").append(net.tag);
        }
        return sb.toString();
    }
    
    // Inner classes for structured data
    private static class NetworkInterface {
        final String model;
        final String bridge;
        final Integer vlan;
        final String tag;
        
        NetworkInterface(String model, String bridge, Integer vlan, String tag) {
            this.model = model;
            this.bridge = bridge;
            this.vlan = vlan;
            this.tag = tag;
        }
    }
    
    private static class DiskConfig {
        final String storage;
        final int sizeGB;
        
        DiskConfig(String storage, int sizeGB) {
            this.storage = storage;
            this.sizeGB = sizeGB;
        }
    }
    
    private static class IpConfig {
        final String config;
        
        IpConfig(String config) {
            this.config = config;
        }
    }
}