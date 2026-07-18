package com.kyra.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An immutable record of an admin action (kyra-doc/modules/12). Append-only. */
@Entity
@Table(schema = "admin_ops", name = "admin_actions")
public class AdminActionEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "admin_id", nullable = false, updatable = false, length = 26)
    public String adminId;

    @Column(name = "action_type", nullable = false, updatable = false, length = 48)
    public String actionType;

    @Column(name = "target_type", updatable = false, length = 32)
    public String targetType;

    @Column(name = "target_id", updatable = false)
    public String targetId;

    @Column(updatable = false)
    public String reason;

    @Column(nullable = false, updatable = false)
    public Instant at;
}
