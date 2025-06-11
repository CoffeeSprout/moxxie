package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SnapshotsResponse {
    private List<Snapshot> data;

    public List<Snapshot> getData() {
        return data;
    }

    public void setData(List<Snapshot> data) {
        this.data = data;
    }
}