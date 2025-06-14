package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskListData {
    private List<TaskStatusData> data;

    public List<TaskStatusData> getData() {
        return data;
    }

    public void setData(List<TaskStatusData> data) {
        this.data = data;
    }
}