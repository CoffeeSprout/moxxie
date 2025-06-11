package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

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