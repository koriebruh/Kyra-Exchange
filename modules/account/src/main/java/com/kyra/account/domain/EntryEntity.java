package com.kyra.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One signed leg of a journal. Append-only — never updated or deleted. */
@Entity
@Table(schema = "account", name = "entries")
public class EntryEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 26)
    public String id;

    @Column(name = "journal_id", nullable = false, updatable = false, length = 26)
    public String journalId;

    @Column(name = "account_key", nullable = false, updatable = false)
    public String accountKey;

    @Column(nullable = false, updatable = false, length = 10)
    public String asset;

    @Column(nullable = false, updatable = false, precision = 38, scale = 18)
    public BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
