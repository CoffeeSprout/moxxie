package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StoragePool {
    private String storage; // Name of the storage pool
    private long total;     // Total capacity in bytes
    private long used;      // Used capacity in bytes

    // Available capacity in bytes
    @JsonProperty("avail")
    private long avail;

    // Storage type (e.g., "nfs", "zfspool", "dir", etc.)
    private String type;

    // Additional fields from the JSON response
    private int active;
    private String content;
    private int shared;

    @JsonProperty("used_fraction")
    private double usedFraction;

    // Getters and setters
    public String getStorage() {
        return storage;
    }
    public void setStorage(String storage) {
        this.storage = storage;
    }
    public long getTotal() {
        return total;
    }
    public void setTotal(long total) {
        this.total = total;
    }
    public long getUsed() {
        return used;
    }
    public void setUsed(long used) {
        this.used = used;
    }
    public long getAvail() {
        return avail;
    }
    public void setAvail(long avail) {
        this.avail = avail;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public int getActive() {
        return active;
    }
    public void setActive(int active) {
        this.active = active;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public int getShared() {
        return shared;
    }
    public void setShared(int shared) {
        this.shared = shared;
    }
    public double getUsedFraction() {
        return usedFraction;
    }
    public void setUsedFraction(double usedFraction) {
        this.usedFraction = usedFraction;
    }
}