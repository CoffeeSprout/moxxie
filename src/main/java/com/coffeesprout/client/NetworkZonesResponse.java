package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkZonesResponse {
    private List<NetworkZone> data;

    @JsonProperty("data")
    public List<NetworkZone> getData() {
        return data;
    }

    public void setData(List<NetworkZone> data) {
        this.data = data;
    }
}
