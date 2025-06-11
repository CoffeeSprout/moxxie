package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskStatusResponse {
    private String data;  // Contains the UPID

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}