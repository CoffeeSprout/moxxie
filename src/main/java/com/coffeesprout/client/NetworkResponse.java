package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

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