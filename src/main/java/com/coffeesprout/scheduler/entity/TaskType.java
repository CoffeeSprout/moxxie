package com.coffeesprout.scheduler.entity;

import java.time.Instant;

import jakarta.persistence.*;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "task_types")
public class TaskType extends PanacheEntity {

    @Column(nullable = false, unique = true, length = 50)
    public String name;

    @Column(name = "display_name", nullable = false, length = 100)
    public String displayName;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "task_class", nullable = false)
    public String taskClass;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static TaskType findByName(String name) {
        return find("name", name).firstResult();
    }
}
