package com.kyra.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Versioned record of every market config change (kyra-doc/modules/03, F3). */
@Entity
@Table(schema = "market", name = "config_history")
public class ConfigHistoryEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 16)
    public String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    public String entityId;

    @Column(name = "changed_by", nullable = false, updatable = false)
    public String changedBy;

    @Column(name = "old_value", updatable = false)
    public String oldValue;

    @Column(name = "new_value", nullable = false, updatable = false)
    public String newValue;

    @Column(nullable = false, updatable = false)
    public Instant at;
}
