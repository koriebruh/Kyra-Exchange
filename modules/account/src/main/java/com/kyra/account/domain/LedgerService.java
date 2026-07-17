package com.kyra.account.domain;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.Balance;
import com.kyra.account.api.BalanceChanged;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.InsufficientBalanceException;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The double-entry ledger (kyra-doc/modules/02). Every mutation is a balanced
 * journal applied in one transaction. Guarantees:
 * <ol>
 *   <li>value conservation — a journal nets to zero per asset (checked in
 *       {@link JournalRequest} and by a DB trigger);</li>
 *   <li>no negative balance — checked here before write and by a DB CHECK;</li>
 *   <li>idempotency by {@code (type, reference)};</li>
 *   <li>deadlock-free — per-account locks are always taken in key order.</li>
 * </ol>
 */
@ApplicationScoped
public class LedgerService implements AccountApi {

    private final EntityManager em;
    private final Event<BalanceChanged> balanceChanged;

    public LedgerService(EntityManager em, Event<BalanceChanged> balanceChanged) {
        this.em = em;
        this.balanceChanged = balanceChanged;
    }

    @Override
    @Transactional
    public String post(JournalRequest request) {
        String existing = findJournalId(request.type(), request.reference());
        if (existing != null) {
            return existing; // idempotent: already applied
        }

        // Net the lines per account so each account is locked and written once.
        Map<String, BigDecimal> deltaByAccount = new TreeMap<>();
        Map<String, String> assetByAccount = new TreeMap<>();
        for (EntryLine line : request.lines()) {
            String key = line.account().value();
            deltaByAccount.merge(key, line.amount().amount(), BigDecimal::add);
            assetByAccount.put(key, line.amount().asset().symbol());
        }

        Instant now = Instant.now();
        String journalId = Ids.newUlid();
        persistJournal(journalId, request.type(), request.reference(), now);
        for (EntryLine line : request.lines()) {
            persistEntry(journalId, line, now);
        }

        // Locks acquired in ascending key order (TreeMap) — deadlock-free.
        for (var e : deltaByAccount.entrySet()) {
            applyDelta(e.getKey(), assetByAccount.get(e.getKey()), e.getValue(), now);
        }

        em.flush(); // surface constraint violations inside this call
        fireBalanceEvents(deltaByAccount.keySet(), request.type());
        return journalId;
    }

    @Override
    @Transactional
    public String hold(String userId, Money amount, String reference) {
        amount.requireNonNegative();
        return post(new JournalRequest(JournalType.ORDER_HOLD, reference, List.of(
                EntryLine.of(AccountKey.userMain(userId, amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userHold(userId, amount.asset()), amount))));
    }

    @Override
    @Transactional
    public String release(String userId, Money amount, String reference) {
        amount.requireNonNegative();
        return post(new JournalRequest(JournalType.HOLD_RELEASE, reference, List.of(
                EntryLine.of(AccountKey.userHold(userId, amount.asset()), amount.negated()),
                EntryLine.of(AccountKey.userMain(userId, amount.asset()), amount))));
    }

    @Override
    @Transactional
    public Balance balanceOf(String userId, AssetId asset) {
        Money available = Money.of(asset, readBalance(AccountKey.userMain(userId, asset).value()));
        Money onHold = Money.of(asset, readBalance(AccountKey.userHold(userId, asset).value()));
        return new Balance(asset, available, onHold);
    }

    // ----- persistence helpers -----

    private String findJournalId(JournalType type, String reference) {
        try {
            return em.createQuery(
                            "select j.id from JournalEntity j where j.type = :t and j.reference = :r", String.class)
                    .setParameter("t", type)
                    .setParameter("r", reference)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private void persistJournal(String id, JournalType type, String reference, Instant now) {
        JournalEntity j = new JournalEntity();
        j.id = id;
        j.type = type;
        j.reference = reference;
        j.createdAt = now;
        em.persist(j);
    }

    private void persistEntry(String journalId, EntryLine line, Instant now) {
        EntryEntity e = new EntryEntity();
        e.id = Ids.newUlid();
        e.journalId = journalId;
        e.accountKey = line.account().value();
        e.asset = line.amount().asset().symbol();
        e.amount = line.amount().amount();
        e.createdAt = now;
        em.persist(e);
    }

    /**
     * Applies one account's net change race-safely:
     * <ol>
     *   <li>ensure the balance row exists via an atomic {@code INSERT ... ON
     *       CONFLICT DO NOTHING} (two first-time writers can't both insert);</li>
     *   <li>lock it with {@code SELECT ... FOR UPDATE} to serialize updaters;</li>
     *   <li>guard non-negativity for user accounts, then update.</li>
     * </ol>
     * System/contra accounts ({@code external:*}, {@code kyra:*}) may go
     * negative — they are the other side of user deposits and holds.
     */
    private void applyDelta(String accountKey, String asset, BigDecimal delta, Instant now) {
        em.createNativeQuery(
                        "insert into account.balances(account_key, asset, amount, updated_at) "
                                + "values (:k, :a, 0, :ts) on conflict (account_key) do nothing")
                .setParameter("k", accountKey)
                .setParameter("a", asset)
                .setParameter("ts", now)
                .executeUpdate();

        BigDecimal current = lockBalance(accountKey);
        BigDecimal next = current.add(delta);
        if (next.signum() < 0 && accountKey.startsWith("user:")) {
            throw new InsufficientBalanceException(accountKey);
        }
        em.createNativeQuery(
                        "update account.balances set amount = :amt, updated_at = :ts where account_key = :k")
                .setParameter("amt", next)
                .setParameter("ts", now)
                .setParameter("k", accountKey)
                .executeUpdate();
    }

    private BigDecimal lockBalance(String accountKey) {
        return (BigDecimal) em.createNativeQuery(
                        "select amount from account.balances where account_key = :k for update")
                .setParameter("k", accountKey)
                .getSingleResult();
    }

    private BigDecimal readBalance(String accountKey) {
        List<?> rows = em.createNativeQuery(
                        "select amount from account.balances where account_key = :k")
                .setParameter("k", accountKey)
                .getResultList();
        return rows.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rows.get(0);
    }

    private void fireBalanceEvents(Iterable<String> accountKeys, JournalType cause) {
        for (String key : accountKeys) {
            String[] parts = key.split(":");
            // user:<id>:<asset>:<type> — emit one event per affected user+asset (on main)
            if (parts.length == 4 && parts[0].equals("user") && parts[3].equals("main")) {
                String userId = parts[1];
                AssetId asset = AssetId.of(parts[2]);
                Balance b = balanceOf(userId, asset);
                balanceChanged.fire(new BalanceChanged(userId, asset, b.available(), b.onHold(), cause));
            }
        }
    }
}
