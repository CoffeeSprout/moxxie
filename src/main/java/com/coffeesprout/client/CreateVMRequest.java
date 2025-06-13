package com.coffeesprout.client;

import jakarta.ws.rs.FormParam;

public class CreateVMRequest {
    @FormParam("vmid")
    private int vmid;

    @FormParam("name")
    private String name;

    @FormParam("cores")
    private int cores;

    @FormParam("memory")
    private int memory; // in MB

    @FormParam("net0")
    private String net0; // e.g., "virtio,bridge=vmbr0"
    
    @FormParam("scsihw")
    private String scsihw = "virtio-scsi-pci"; // Default SCSI hardware
    
    @FormParam("scsi0")
    private String scsi0; // Disk configuration
    
    @FormParam("ide2")
    private String ide2; // Cloud-init drive
    
    @FormParam("boot")
    private String boot = "c"; // Boot from hard disk by default
    
    @FormParam("onboot")
    private int onboot; // Start on boot (0 or 1)
    
    @FormParam("pool")
    private String pool; // Resource pool name (optional)
    
    @FormParam("tags")
    private String tags; // Comma-separated tags

    public int getVmid() {
        return vmid;
    }
    public void setVmid(int vmid) {
        this.vmid = vmid;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getCores() {
        return cores;
    }
    public void setCores(int cores) {
        this.cores = cores;
    }
    public int getMemory() {
        return memory;
    }
    public void setMemory(int memory) {
        this.memory = memory;
    }
    public String getNet0() {
        return net0;
    }
    public void setNet0(String net0) {
        this.net0 = net0;
    }
    public String getScsihw() {
        return scsihw;
    }
    public void setScsihw(String scsihw) {
        this.scsihw = scsihw;
    }
    public String getScsi0() {
        return scsi0;
    }
    public void setScsi0(String scsi0) {
        this.scsi0 = scsi0;
    }
    public String getIde2() {
        return ide2;
    }
    public void setIde2(String ide2) {
        this.ide2 = ide2;
    }
    public String getBoot() {
        return boot;
    }
    public void setBoot(String boot) {
        this.boot = boot;
    }
    public int getOnboot() {
        return onboot;
    }
    public void setOnboot(int onboot) {
        this.onboot = onboot;
    }
    public String getPool() {
        return pool;
    }
    public void setPool(String pool) {
        this.pool = pool;
    }
    public String getTags() {
        return tags;
    }
    public void setTags(String tags) {
        this.tags = tags;
    }
}