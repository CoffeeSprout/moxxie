package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VM {
    private int vmid;
    private String name;
    private String status;
    private int cpus;
    @JsonProperty("maxmem")
    private long maxmem;
    @JsonProperty("maxdisk")
    private long maxdisk;
    private String node;
    private String type;
    private long uptime;
    private double cpu;
    private long mem;
    private long disk;
    private long netin;
    private long netout;

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
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public int getCpus() {
        return cpus;
    }
    public void setCpus(int cpus) {
        this.cpus = cpus;
    }
    public long getMaxmem() {
        return maxmem;
    }
    public void setMaxmem(long maxmem) {
        this.maxmem = maxmem;
    }
    public long getMaxdisk() {
        return maxdisk;
    }
    public void setMaxdisk(long maxdisk) {
        this.maxdisk = maxdisk;
    }
    public String getNode() {
        return node;
    }
    public void setNode(String node) {
        this.node = node;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public long getUptime() {
        return uptime;
    }
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }
    public double getCpu() {
        return cpu;
    }
    public void setCpu(double cpu) {
        this.cpu = cpu;
    }
    public long getMem() {
        return mem;
    }
    public void setMem(long mem) {
        this.mem = mem;
    }
    public long getDisk() {
        return disk;
    }
    public void setDisk(long disk) {
        this.disk = disk;
    }
    public long getNetin() {
        return netin;
    }
    public void setNetin(long netin) {
        this.netin = netin;
    }
    public long getNetout() {
        return netout;
    }
    public void setNetout(long netout) {
        this.netout = netout;
    }
}
