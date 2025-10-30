package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Node {
    @JsonProperty("node")
    private String name;
    // Optionally include other fields returned by the API
    private int cpu;
    @JsonProperty("maxmem")
    private long maxmem; // bytes
    @JsonProperty("status")
    private String status;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getCpu() {
        return cpu;
    }
    public void setCpu(int cpu) {
        this.cpu = cpu;
    }
    public long getMaxmem() {
        return maxmem;
    }
    public void setMaxmem(long maxmem) {
        this.maxmem = maxmem;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
