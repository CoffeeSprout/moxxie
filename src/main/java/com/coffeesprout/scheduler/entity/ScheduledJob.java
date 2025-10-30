package com.coffeesprout.scheduler.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Parameters;

@Entity
@Table(name = "scheduled_jobs")
@NamedQueries({
    @NamedQuery(name = "ScheduledJob.findEnabled",
                query = "SELECT j FROM ScheduledJob j WHERE j.enabled = true"),
    @NamedQuery(name = "ScheduledJob.findByTaskType",
                query = "SELECT j FROM ScheduledJob j WHERE j.taskType.name = :taskTypeName")
})
public class ScheduledJob extends PanacheEntity {

    @Column(nullable = false, unique = true, length = 200)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "task_type_id")
    public TaskType taskType;

    @Column(name = "cron_expression", nullable = false, length = 120)
    public String cronExpression;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(name = "max_retries", nullable = false)
    public int maxRetries = 3;

    @Column(name = "retry_delay_seconds", nullable = false)
    public int retryDelaySeconds = 300;

    @Column(name = "timeout_seconds", nullable = false)
    public int timeoutSeconds = 3600;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    @Column(name = "created_by", length = 200)
    public String createdBy;

    @Column(name = "updated_by", length = 200)
    public String updatedBy;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JobParameter> parameters = new ArrayList<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JobVMSelector> vmSelectors = new ArrayList<>();

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY)
    @OrderBy("startedAt DESC")
    public List<JobExecution> executions = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public static List<ScheduledJob> findEnabled() {
        return list("enabled", true);
    }

    public static ScheduledJob findByName(String name) {
        return find("name", name).firstResult();
    }

    public static List<ScheduledJob> findByTaskType(String taskTypeName) {
        return find("#ScheduledJob.findByTaskType",
                    Parameters.with("taskTypeName", taskTypeName)).list();
    }

    public void addParameter(String key, String value) {
        JobParameter param = new JobParameter();
        param.job = this;
        param.paramKey = key;
        param.paramValue = value;
        parameters.add(param);
    }

    public String getParameter(String key) {
        return parameters.stream()
            .filter(p -> p.paramKey.equals(key))
            .map(p -> p.paramValue)
            .findFirst()
            .orElse(null);
    }

    public void addVMSelector(String type, String value, String excludeExpression) {
        JobVMSelector selector = new JobVMSelector();
        selector.job = this;
        selector.selectorType = type;
        selector.selectorValue = value;
        selector.excludeExpression = excludeExpression;
        vmSelectors.add(selector);
    }
}
