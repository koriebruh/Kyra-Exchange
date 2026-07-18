package com.kyra.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One admin's approval of a withdrawal (kyra-doc/modules/12, 4-eyes). The unique
 * (withdrawal, admin) constraint means a single admin cannot supply two of the
 * required approvals.
 */
@Entity
@Table(
        schema = "admin_ops",
        name = "withdrawal_approvals",
        uniqueConstraints = @UniqueConstraint(name = "uq_withdrawal_approver",
                columnNames = {"withdrawal_id", "admin_id"}))
public class WithdrawalApprovalEntity {

    @Id
    @Column(length = 26, updatable = false)
    public String id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, length = 26)
    public String withdrawalId;

    @Column(name = "admin_id", nullable = false, updatable = false, length = 26)
    public String adminId;

    @Column(name = "approved_at", nullable = false, updatable = false)
    public Instant approvedAt;
}
