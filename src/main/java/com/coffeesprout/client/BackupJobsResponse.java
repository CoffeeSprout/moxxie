package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackupJobsResponse {
    private List<BackupJobData> data;

    public List<BackupJobData> getData() {
        return data;
    }

    public void setData(List<BackupJobData> data) {
        this.data = data;
    }
}