package com.kyra.wallet.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/**
 * Deposits and withdrawals (kyra-doc/modules/08). The ledger is the source of
 * truth for user balances; this module moves value between the ledger and the
 * outside world (custody) and keeps the two reconcilable.
 */
public interface WalletApi {

    /** Get or create the user's deposit address for an asset. */
    String depositAddress(String userId, AssetId asset);

    /**
     * Credit a confirmed on-chain deposit to the user's ledger balance. Called by
     * the deposit detector (webhook/poll) once the required confirmations are in.
     * Idempotent by {@code txid} — a replayed deposit credits once.
     */
    void creditDeposit(String userId, Money amount, String txid);

    /**
     * Request a withdrawal: holds the amount + fee, then submits to custody.
     * Funds move to hold immediately; they leave the ledger only when the
     * withdrawal completes on-chain.
     *
     * @return the withdrawal id
     * @throws com.kyra.account.api.InsufficientBalanceException if funds are short
     */
    String requestWithdrawal(String userId, AssetId asset, Money amount, String toAddress);

    /**
     * How many distinct admin approvals a withdrawal needs before it may be
     * submitted (kyra-doc/modules/12, 4-eyes): 2 above the dual-approval
     * threshold, otherwise 1.
     */
    int requiredApprovals(String withdrawId);

    /**
     * Approve a withdrawal awaiting manual review and submit it to custody
     * (kyra-doc/modules/08, F3). Called by the admin module. No-op unless the
     * withdrawal is in PENDING_REVIEW.
     */
    void approveWithdrawal(String withdrawId);

    /** Reject a withdrawal awaiting review: release the held funds. Called by admin. */
    void rejectWithdrawal(String withdrawId, String reason);

    /** Finalize a withdrawal confirmed on-chain: held funds leave to external, fee to the exchange. */
    void completeWithdrawal(String withdrawId, String txid);

    /** Fail a withdrawal (rejected/failed on-chain): release the held funds back to the user. */
    void failWithdrawal(String withdrawId, String reason);

    /**
     * Reconcile total user liabilities against custody for an asset
     * (kyra-doc/modules/08, F4). A not-covered result is a critical alarm — never
     * auto-fixed.
     */
    ReconciliationResult reconcile(AssetId asset);
}
