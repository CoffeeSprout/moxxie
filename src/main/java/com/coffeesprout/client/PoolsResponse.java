package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolsResponse {
    private List<Pool> data;

    public List<Pool> getData() {
        return data;
    }

    public void setData(List<Pool> data) {
        this.data = data;
    }
}
