package com.coffeesprout.scheduler.task;

import com.coffeesprout.scheduler.entity.JobExecution;
import com.coffeesprout.scheduler.entity.ScheduledJob;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object passed to scheduled tasks during execution
 */
public class TaskContext {
    
    private ScheduledJob job;
    private JobExecution execution;
    private boolean manualTrigger;
    private Map<String, String> parameters = new HashMap<>();
    private Map<String, Object> attributes = new HashMap<>();
    
    public ScheduledJob getJob() {
        return job;
    }
    
    public void setJob(ScheduledJob job) {
        this.job = job;
    }
    
    public JobExecution getExecution() {
        return execution;
    }
    
    public void setExecution(JobExecution execution) {
        this.execution = execution;
    }
    
    public boolean isManualTrigger() {
        return manualTrigger;
    }
    
    public void setManualTrigger(boolean manualTrigger) {
        this.manualTrigger = manualTrigger;
    }
    
    public void addParameter(String key, String value) {
        parameters.put(key, value);
    }
    
    public String getParameter(String key) {
        return parameters.get(key);
    }
    
    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }
    
    public Integer getIntParameter(String key, Integer defaultValue) {
        String value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public Boolean getBooleanParameter(String key, Boolean defaultValue) {
        String value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    public Map<String, String> getParameters() {
        return new HashMap<>(parameters);
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException("Attribute " + key + " is not of type " + type.getName());
        }
        return (T) value;
    }
    
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
}