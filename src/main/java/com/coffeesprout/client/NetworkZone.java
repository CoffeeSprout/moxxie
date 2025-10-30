package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkZone {
    private String zone;
    private String type;
    private String ipam;
    private String dns;
    private String reversedns;
    private String dnszone;
    private Integer nodes;
    private String pending;

    @JsonProperty("zone")
    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("ipam")
    public String getIpam() {
        return ipam;
    }

    public void setIpam(String ipam) {
        this.ipam = ipam;
    }

    @JsonProperty("dns")
    public String getDns() {
        return dns;
    }

    public void setDns(String dns) {
        this.dns = dns;
    }

    @JsonProperty("reversedns")
    public String getReversedns() {
        return reversedns;
    }

    public void setReversedns(String reversedns) {
        this.reversedns = reversedns;
    }

    @JsonProperty("dnszone")
    public String getDnszone() {
        return dnszone;
    }

    public void setDnszone(String dnszone) {
        this.dnszone = dnszone;
    }

    @JsonProperty("nodes")
    public Integer getNodes() {
        return nodes;
    }

    public void setNodes(Integer nodes) {
        this.nodes = nodes;
    }

    @JsonProperty("pending")
    public String getPending() {
        return pending;
    }

    public void setPending(String pending) {
        this.pending = pending;
    }
}
