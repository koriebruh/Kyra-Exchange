package com.kyra.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Delivery record — one row per (deduplicated) notification. */
@Entity
@Table(schema = "notification", name = "notifications")
public class NotificationEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(nullable = false, updatable = false, length = 32)
    public String type;

    @Column(name = "to_email", nullable = false, updatable = false)
    public String toEmail;

    @Column(name = "dedup_key", nullable = false, unique = true, updatable = false)
    public String dedupKey;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    public Instant sentAt;
}
