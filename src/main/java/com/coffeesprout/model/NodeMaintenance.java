package com.coffeesprout.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Entity
@Table(name = "node_maintenance")
public class NodeMaintenance extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "node_maintenance_seq")
    @SequenceGenerator(name = "node_maintenance_seq", sequenceName = "node_maintenance_SEQ", allocationSize = 50)
    public Long id;

    @Column(name = "node_name", nullable = false)
    public String nodeName;

    @Column(name = "in_maintenance", nullable = false)
    public Boolean inMaintenance;

    @Column(name = "maintenance_started")
    public Instant maintenanceStarted;

    @Column(name = "maintenance_ended")
    public Instant maintenanceEnded;

    @Column(name = "reason", columnDefinition = "TEXT")
    public String reason;

    @Column(name = "initiated_by")
    public String initiatedBy;

    @Column(name = "last_drain_id")
    public String lastDrainId;

    @Column(name = "drain_status")
    public String drainStatus; // 'none', 'in_progress', 'completed', 'failed'

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vm_list", columnDefinition = "jsonb")
    public List<Integer> vmList; // VMs that were on this node before drain

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    public Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // Helper methods

    /**
     * Find active maintenance record for a node.
     */
    public static Optional<NodeMaintenance> findActiveByNode(String nodeName) {
        return find("nodeName = ?1 AND inMaintenance = true", nodeName).firstResultOptional();
    }

    /**
     * Find latest maintenance record for a node (active or historical).
     */
    public static Optional<NodeMaintenance> findLatestByNode(String nodeName) {
        return find("nodeName = ?1 ORDER BY updatedAt DESC", nodeName).firstResultOptional();
    }

    /**
     * Find all maintenance records for a node.
     */
    public static List<NodeMaintenance> findByNode(String nodeName) {
        return list("nodeName = ?1 ORDER BY maintenanceStarted DESC", nodeName);
    }

    /**
     * Find all nodes currently in maintenance.
     */
    public static List<NodeMaintenance> findAllInMaintenance() {
        return list("inMaintenance = true ORDER BY maintenanceStarted DESC");
    }

    /**
     * Check if a node is in maintenance.
     */
    public static boolean isNodeInMaintenance(String nodeName) {
        return count("nodeName = ?1 AND inMaintenance = true", nodeName) > 0;
    }

    // Lifecycle callback
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
