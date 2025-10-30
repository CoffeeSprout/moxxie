package com.coffeesprout.client;


import java.util.List;

public class StorageResponse {
    private List<StoragePool> data;

    public List<StoragePool> getData() {
        return data;
    }
    public void setData(List<StoragePool> data) {
        this.data = data;
    }
}
