package com.kyra.wallet.domain;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.fee.api.FeeApi;
import com.kyra.wallet.api.CustodyProvider;
import com.kyra.wallet.api.WalletApi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deposits and withdrawals against the ledger (kyra-doc/modules/08). Deposits
 * credit the ledger from the external contra account once confirmed; withdrawals
 * hold funds on request and move them out only when the on-chain transaction
 * completes. Every money movement is a balanced ledger journal; deposit credits
 * are idempotent by txid, and withdrawal state transitions are one-way.
 */
@ApplicationScoped
public class WalletService implements WalletApi {

    private static final Logger LOG = Logger.getLogger(WalletService.class);

    private final EntityManager em;
    private final AccountApi ledger;
    private final FeeApi fees;
    private final CustodyProvider custody;

    public WalletService(EntityManager em, AccountApi ledger, FeeApi fees, CustodyProvider custody) {
        this.em = em;
        this.ledger = ledger;
        this.fees = fees;
        this.custody = custody;
    }

    @Override
    @Transactional
    public String depositAddress(String userId, AssetId asset) {
        DepositAddressEntity.Key key = new DepositAddressEntity.Key();
        key.userId = userId;
        key.asset = asset.symbol();
        DepositAddressEntity existing = em.find(DepositAddressEntity.class, key);
        if (existing != null) {
            return existing.address;
        }
        DepositAddressEntity e = new DepositAddressEntity();
        e.userId = userId;
        e.asset = asset.symbol();
        e.address = custody.depositAddress(userId, asset);
        e.createdAt = Instant.now();
        em.persist(e);
        return e.address;
    }

    @Override
    @Transactional
    public void creditDeposit(String userId, Money amount, String txid) {
        amount.requireNonNegative();
        if (findDepositByTxid(txid) != null) {
            return; // already credited (idempotent)
        }
        ledger.post(new JournalRequest(JournalType.DEPOSIT, txid, List.of(
                EntryLine.of(AccountKey.external(amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userMain(userId, amount.asset()), amount))));

        DepositEntity d = new DepositEntity();
        d.id = Ids.newUlid();
        d.userId = userId;
        d.asset = amount.asset().symbol();
        d.amount = amount.amount();
        d.txid = txid;
        d.creditedAt = Instant.now();
        em.persist(d);
        LOG.infof("deposit credited: user=%s %s txid=%s", userId, amount, txid);
    }

    @Override
    @Transactional
    public String requestWithdrawal(String userId, AssetId asset, Money amount, String toAddress) {
        amount.requireNonNegative();
        Money fee = Money.of(asset, fees.withdrawFee(asset));
        Money total = amount.plus(fee);

        String id = Ids.newUlid();
        // hold amount + fee up front; throws InsufficientBalanceException if short
        ledger.hold(userId, total, "withdraw:" + id);

        WithdrawalEntity w = new WithdrawalEntity();
        w.id = id;
        w.userId = userId;
        w.asset = asset.symbol();
        w.amount = amount.amount();
        w.fee = fee.amount();
        w.toAddress = toAddress;
        w.status = "PENDING";
        w.requestedAt = Instant.now();
        em.persist(w);

        w.providerRef = custody.submitWithdrawal(id, asset, toAddress, amount);
        w.status = "BROADCASTING";
        LOG.infof("withdrawal submitted: id=%s user=%s %s -> %s", id, userId, amount, toAddress);
        return id;
    }

    @Override
    @Transactional
    public void completeWithdrawal(String withdrawId, String txid) {
        WithdrawalEntity w = em.find(WithdrawalEntity.class, withdrawId);
        if (w == null || !"BROADCASTING".equals(w.status)) {
            return; // unknown or not in-flight (idempotent)
        }
        AssetId asset = AssetId.of(w.asset);
        Money amount = Money.of(asset, w.amount);
        Money fee = Money.of(asset, w.fee);

        // Held (amount + fee) leaves: amount to the outside world, fee to the exchange.
        List<EntryLine> lines = new ArrayList<>();
        lines.add(EntryLine.of(AccountKey.userHold(w.userId, asset), amount.plus(fee).negated()));
        lines.add(EntryLine.of(AccountKey.external(asset), amount));
        if (fee.isPositive()) {
            lines.add(EntryLine.of(AccountKey.fee(asset), fee));
        }
        ledger.post(new JournalRequest(JournalType.WITHDRAW, withdrawId, lines));

        w.status = "COMPLETED";
        w.txid = txid;
        w.completedAt = Instant.now();
        LOG.infof("withdrawal completed: id=%s txid=%s", withdrawId, txid);
    }

    @Override
    @Transactional
    public void failWithdrawal(String withdrawId, String reason) {
        WithdrawalEntity w = em.find(WithdrawalEntity.class, withdrawId);
        if (w == null || "COMPLETED".equals(w.status) || "FAILED".equals(w.status)) {
            return;
        }
        AssetId asset = AssetId.of(w.asset);
        Money total = Money.of(asset, w.amount).plus(Money.of(asset, w.fee));
        ledger.release(w.userId, total, "withdraw-release:" + withdrawId);
        w.status = "FAILED";
        LOG.warnf("withdrawal failed: id=%s reason=%s (funds released)", withdrawId, reason);
    }

    private DepositEntity findDepositByTxid(String txid) {
        try {
            return em.createQuery("from DepositEntity where txid = :t", DepositEntity.class)
                    .setParameter("t", txid).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
