package com.kyra.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.kyra.account.api.JournalType;

import java.time.Instant;

/** A posted journal header. Immutable once written. */
@Entity
@Table(
        schema = "account",
        name = "journals",
        uniqueConstraints = @UniqueConstraint(name = "uq_journal_type_ref", columnNames = {"type", "reference"}))
public class JournalEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 26)
    public String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    public JournalType type;

    @Column(nullable = false, updatable = false)
    public String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
