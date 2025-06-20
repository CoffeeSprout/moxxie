package com.coffeesprout.scheduler.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "job_vm_selectors")
public class JobVMSelector extends PanacheEntity {
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id")
    public ScheduledJob job;
    
    @Column(name = "selector_type", nullable = false, length = 50)
    public String selectorType; // 'tag_expression', 'vm_list', 'all'
    
    @Column(name = "selector_value", nullable = false, columnDefinition = "TEXT")
    public String selectorValue; // tag expression, comma-separated VM IDs, or 'ALL'
    
    @Column(name = "exclude_expression", columnDefinition = "TEXT")
    public String excludeExpression; // optional exclusion expression
    
    public enum SelectorType {
        TAG_EXPRESSION("tag_expression"),
        VM_LIST("vm_list"),
        ALL("all");
        
        private final String value;
        
        SelectorType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static SelectorType fromValue(String value) {
            for (SelectorType type : values()) {
                if (type.value.equals(value) || type.name().equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown selector type: " + value);
        }
    }
}