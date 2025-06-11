package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolMember {
    private String id;
    private String type;
    private String node;
    private int vmid;
    private String name;
    private String storage;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getNode() {
        return node;
    }
    
    public void setNode(String node) {
        this.node = node;
    }
    
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
    
    public String getStorage() {
        return storage;
    }
    
    public void setStorage(String storage) {
        this.storage = storage;
    }
}