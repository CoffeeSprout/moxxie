package com.coffeesprout.scheduler.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "job_vm_executions")
public class JobVMExecution extends PanacheEntity {
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "execution_id")
    public JobExecution execution;
    
    @Column(name = "vm_id", nullable = false)
    public int vmId;
    
    @Column(name = "vm_name", length = 200)
    public String vmName;
    
    @Column(name = "node_name", length = 100)
    public String nodeName;
    
    @Column(nullable = false, length = 50)
    public String status; // 'success', 'failed', 'skipped'
    
    @Column(name = "started_at", nullable = false)
    public Instant startedAt;
    
    @Column(name = "completed_at")
    public Instant completedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;
    
    @Column(name = "result_data", columnDefinition = "JSONB")
    @Convert(converter = JsonMapConverter.class)
    public Map<String, Object> resultData; // Task-specific results
    
    public enum Status {
        SUCCESS("success"),
        FAILED("failed"),
        SKIPPED("skipped");
        
        private final String value;
        
        Status(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }
    
    public void complete(Status status, Map<String, Object> resultData) {
        this.status = status.getValue();
        this.completedAt = Instant.now();
        this.resultData = resultData;
    }
    
    public void fail(String errorMessage) {
        this.status = Status.FAILED.getValue();
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}