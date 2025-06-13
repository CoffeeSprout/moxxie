package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackupJobDetailResponse {
    private BackupJobData data;

    public BackupJobData getData() {
        return data;
    }

    public void setData(BackupJobData data) {
        this.data = data;
    }
}