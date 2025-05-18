package com.coffeesprout.client;

import jakarta.ws.rs.FormParam;

public class CreateVMRequest {
    // Example form parameters – adjust these based on your Proxmox API requirements.
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

    // Add additional parameters as needed.

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
}