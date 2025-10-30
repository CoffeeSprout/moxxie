package com.coffeesprout.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.persistence.*;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vm_migrations")
public class VmMigration extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vm_migrations_seq")
    @SequenceGenerator(name = "vm_migrations_seq", sequenceName = "vm_migrations_SEQ", allocationSize = 50)
    public Long id;

    @Column(name = "vm_id", nullable = false)
    public Integer vmId;

    @Column(name = "vm_name")
    public String vmName;

    @Column(name = "source_node", nullable = false)
    public String sourceNode;

    @Column(name = "target_node", nullable = false)
    public String targetNode;

    @Column(name = "migration_type", nullable = false)
    public String migrationType; // 'online', 'offline'

    @Column(name = "pre_migration_state")
    public String preMigrationState; // 'running', 'stopped'

    @Column(name = "post_migration_state")
    public String postMigrationState;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "duration_seconds")
    public Integer durationSeconds;

    @Column(name = "status", nullable = false)
    public String status; // 'started', 'completed', 'failed'

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "task_upid")
    public String taskUpid;

    @Column(name = "initiated_by")
    public String initiatedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    public Map<String, Object> options;

    // Helper methods
    public static VmMigration findLatestByVmId(Integer vmId) {
        return find("vmId = ?1 ORDER BY startedAt DESC", vmId).firstResult();
    }

    public static List<VmMigration> findByVmId(Integer vmId) {
        return list("vmId = ?1 ORDER BY startedAt DESC", vmId);
    }

    public static List<VmMigration> findBySourceNode(String node) {
        return list("sourceNode = ?1 ORDER BY startedAt DESC", node);
    }

    public static List<VmMigration> findByTargetNode(String node) {
        return list("targetNode = ?1 ORDER BY startedAt DESC", node);
    }

    public void markCompleted(String postState) {
        this.completedAt = Instant.now();
        this.status = "completed";
        this.postMigrationState = postState;
        if (this.startedAt != null) {
            this.durationSeconds = (int) (this.completedAt.getEpochSecond() - this.startedAt.getEpochSecond());
        }
    }

    public void markFailed(String error) {
        this.completedAt = Instant.now();
        this.status = "failed";
        this.errorMessage = error;
        if (this.startedAt != null) {
            this.durationSeconds = (int) (this.completedAt.getEpochSecond() - this.startedAt.getEpochSecond());
        }
    }
}
