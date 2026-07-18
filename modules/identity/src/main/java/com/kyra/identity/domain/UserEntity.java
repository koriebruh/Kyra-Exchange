package com.kyra.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(schema = "identity", name = "users")
public class UserEntity {

    public enum Status { PENDING, ACTIVE, SUSPENDED, CLOSED }

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    @Column(name = "email_verified_at")
    public Instant emailVerifiedAt;

    /** Anti-phishing phrase shown in official emails (kyra-doc/modules/01, F2b). */
    @Column(name = "anti_phishing_code")
    public String antiPhishingCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
