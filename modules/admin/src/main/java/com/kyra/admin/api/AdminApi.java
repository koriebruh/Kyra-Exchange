package com.kyra.admin.api;

/**
 * Backoffice operations (kyra-doc/modules/12). Admin actions never touch other
 * modules' data directly — they call those modules' APIs and record an immutable
 * audit entry. Withdrawal review is the first operation.
 */
public interface AdminApi {

    /** Approve a withdrawal awaiting review and submit it; records an audit entry. */
    void approveWithdrawal(String adminId, String withdrawId);

    /** Reject a withdrawal awaiting review (releases funds); records an audit entry. */
    void rejectWithdrawal(String adminId, String withdrawId, String reason);
}
