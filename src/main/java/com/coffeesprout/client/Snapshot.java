package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Snapshot {
    private String name;
    private String description;
    private String parent;
    @JsonProperty("snaptime")
    private Long snaptime;
    @JsonProperty("vmstate")
    private Integer vmstate;  // Proxmox returns 0 or 1
    private Long size;

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public Long getSnaptime() {
        return snaptime;
    }

    public void setSnaptime(Long snaptime) {
        this.snaptime = snaptime;
    }

    public Integer getVmstate() {
        return vmstate;
    }

    public void setVmstate(Integer vmstate) {
        this.vmstate = vmstate;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public boolean hasVmState() {
        return vmstate != null && vmstate == 1;
    }
}