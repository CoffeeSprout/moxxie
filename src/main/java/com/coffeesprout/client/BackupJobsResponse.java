package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

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
