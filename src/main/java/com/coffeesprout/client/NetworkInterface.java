package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkInterface {
    private String iface;
    private String type;
    private String method;
    private String address;
    private String netmask;
    private String gateway;
    private String bridge_ports;
    private Integer bridge_vlan_aware;
    private String cidr;
    private String comments;
    private Integer active;
    private Integer autostart;
    
    @JsonProperty("bridge-ports")
    public String getBridge_ports() {
        return bridge_ports;
    }
    
    @JsonProperty("bridge-ports")
    public void setBridge_ports(String bridge_ports) {
        this.bridge_ports = bridge_ports;
    }
    
    @JsonProperty("bridge-vlan-aware")
    public Integer getBridge_vlan_aware() {
        return bridge_vlan_aware;
    }
    
    @JsonProperty("bridge-vlan-aware")
    public void setBridge_vlan_aware(Integer bridge_vlan_aware) {
        this.bridge_vlan_aware = bridge_vlan_aware;
    }
    
    public String getIface() {
        return iface;
    }
    
    public void setIface(String iface) {
        this.iface = iface;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getNetmask() {
        return netmask;
    }
    
    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }
    
    public String getGateway() {
        return gateway;
    }
    
    public void setGateway(String gateway) {
        this.gateway = gateway;
    }
    
    public String getCidr() {
        return cidr;
    }
    
    public void setCidr(String cidr) {
        this.cidr = cidr;
    }
    
    public String getComments() {
        return comments;
    }
    
    public void setComments(String comments) {
        this.comments = comments;
    }
    
    public Integer getActive() {
        return active;
    }
    
    public void setActive(Integer active) {
        this.active = active;
    }
    
    public Integer getAutostart() {
        return autostart;
    }
    
    public void setAutostart(Integer autostart) {
        this.autostart = autostart;
    }
}