package com.kyra.app.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One immutable audit record. */
@Entity
@Table(schema = "audit", name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "actor_user_id", length = 26, updatable = false)
    public String actorUserId;

    @Column(nullable = false, updatable = false, length = 64)
    public String action;

    @Column(name = "target_type", updatable = false, length = 64)
    public String targetType;

    @Column(name = "target_id", updatable = false)
    public String targetId;

    @Column(updatable = false)
    public String ip;

    @Column(updatable = false)
    public String detail;

    @Column(nullable = false, updatable = false)
    public Instant at;
}
