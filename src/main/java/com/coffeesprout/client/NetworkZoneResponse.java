package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkZoneResponse {
    private NetworkZone data;

    @JsonProperty("data")
    public NetworkZone getData() {
        return data;
    }

    public void setData(NetworkZone data) {
        this.data = data;
    }
}
