package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNetsResponse {
    private List<VNet> data;

    @JsonProperty("data")
    public List<VNet> getData() {
        return data;
    }

    public void setData(List<VNet> data) {
        this.data = data;
    }
}
