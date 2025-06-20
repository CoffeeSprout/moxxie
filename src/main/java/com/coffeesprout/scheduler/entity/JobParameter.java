package com.coffeesprout.scheduler.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "job_parameters", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "param_key"}))
public class JobParameter extends PanacheEntity {
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id")
    public ScheduledJob job;
    
    @Column(name = "param_key", nullable = false, length = 100)
    public String paramKey;
    
    @Column(name = "param_value", nullable = false, columnDefinition = "TEXT")
    public String paramValue;
}