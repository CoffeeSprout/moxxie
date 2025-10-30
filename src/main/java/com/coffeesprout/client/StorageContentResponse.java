package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageContentResponse {
    private List<StorageContent> data;

    public List<StorageContent> getData() {
        return data;
    }

    public void setData(List<StorageContent> data) {
        this.data = data;
    }
}
