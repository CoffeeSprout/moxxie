package com.coffeesprout.scheduler.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "job_executions")
@NamedQueries({
    @NamedQuery(name = "JobExecution.findByStatus",
                query = "SELECT e FROM JobExecution e WHERE e.status = :status ORDER BY e.startedAt DESC"),
    @NamedQuery(name = "JobExecution.findRunning",
                query = "SELECT e FROM JobExecution e WHERE e.status = 'running' ORDER BY e.startedAt DESC")
})
public class JobExecution extends PanacheEntity {
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id")
    public ScheduledJob job;
    
    @Column(name = "execution_id", nullable = false, unique = true, length = 100)
    public String executionId; // Quartz fire instance ID
    
    @Column(nullable = false, length = 50)
    public String status; // 'running', 'completed', 'failed', 'cancelled'
    
    @Column(name = "started_at", nullable = false)
    public Instant startedAt;
    
    @Column(name = "completed_at")
    public Instant completedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;
    
    @Column(name = "processed_vms")
    public int processedVMs = 0;
    
    @Column(name = "successful_vms")
    public int successfulVMs = 0;
    
    @Column(name = "failed_vms")
    public int failedVMs = 0;
    
    @Column(name = "execution_details", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    public Map<String, Object> executionDetails;
    
    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JobVMExecution> vmExecutions = new ArrayList<>();
    
    public enum Status {
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELLED("cancelled");
        
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
    
    public static JobExecution findByExecutionId(String executionId) {
        return find("executionId", executionId).firstResult();
    }
    
    public static List<JobExecution> findByStatus(String status) {
        return find("#JobExecution.findByStatus", 
                    Parameters.with("status", status)).list();
    }
    
    public static List<JobExecution> findRunning() {
        return find("#JobExecution.findRunning").list();
    }
    
    public static List<JobExecution> findByJob(Long jobId, int limit) {
        return find("job.id", Sort.descending("startedAt"), jobId)
            .page(0, limit)
            .list();
    }
    
    public void complete() {
        this.status = Status.COMPLETED.getValue();
        this.completedAt = Instant.now();
    }
    
    public void fail(String errorMessage) {
        this.status = Status.FAILED.getValue();
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
    
    public void cancel() {
        this.status = Status.CANCELLED.getValue();
        this.completedAt = Instant.now();
    }
    
    public void incrementProcessed() {
        this.processedVMs++;
    }
    
    public void incrementSuccessful() {
        this.successfulVMs++;
    }
    
    public void incrementFailed() {
        this.failedVMs++;
    }
}