package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkResponse {
    private List<NetworkInterface> data;

    public List<NetworkInterface> getData() {
        return data;
    }

    public void setData(List<NetworkInterface> data) {
        this.data = data;
    }
}
