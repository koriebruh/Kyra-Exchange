package com.kyra.settlement.domain;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.money.Money;
import com.kyra.settlement.api.SettlementApi;
import com.kyra.settlement.api.TradeSettled;
import com.kyra.settlement.api.TradeSettlement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Settles executed trades to the ledger (kyra-doc/modules/06). Each trade posts
 * one balanced double-entry journal — buyer's held quote pays the seller, the
 * seller's held base goes to the buyer — and records the trade. Idempotent by
 * {@code tradeId}: the journal reference and the trade row both key on it, so a
 * replayed or retried settlement is a no-op.
 */
@ApplicationScoped
public class SettlementService implements SettlementApi {

    private static final Logger LOG = Logger.getLogger(SettlementService.class);

    private final EntityManager em;
    private final AccountApi ledger;
    private final Event<TradeSettled> tradeSettled;

    public SettlementService(EntityManager em, AccountApi ledger, Event<TradeSettled> tradeSettled) {
        this.em = em;
        this.ledger = ledger;
        this.tradeSettled = tradeSettled;
    }

    @Override
    @Transactional
    public void settle(TradeSettlement t) {
        if (em.find(TradeEntity.class, t.tradeId()) != null) {
            return; // already settled
        }

        Money quote = t.quoteAmount();
        Money base = t.baseQty();
        Money sellerFee = t.sellerFee();
        Money buyerFee = t.buyerFee();

        // Quote side: buyer's held quote pays the seller minus fee; fee -> kyra:fee.
        // Base side: seller's held base goes to the buyer minus fee; fee -> kyra:fee.
        // Nets to zero per asset. Zero-fee entries are omitted (a journal line must be non-zero).
        List<EntryLine> lines = new ArrayList<>();
        lines.add(EntryLine.of(AccountKey.userHold(t.buyerUserId(), quote.asset()), quote.negated()));
        lines.add(EntryLine.of(AccountKey.userMain(t.sellerUserId(), quote.asset()), quote.minus(sellerFee)));
        if (sellerFee.isPositive()) {
            lines.add(EntryLine.of(AccountKey.fee(quote.asset()), sellerFee));
        }
        lines.add(EntryLine.of(AccountKey.userHold(t.sellerUserId(), base.asset()), base.negated()));
        lines.add(EntryLine.of(AccountKey.userMain(t.buyerUserId(), base.asset()), base.minus(buyerFee)));
        if (buyerFee.isPositive()) {
            lines.add(EntryLine.of(AccountKey.fee(base.asset()), buyerFee));
        }
        ledger.post(new JournalRequest(JournalType.TRADE_SETTLEMENT, t.tradeId(), lines));

        TradeEntity e = new TradeEntity();
        e.id = t.tradeId();
        e.pair = t.pair().toString();
        e.baseQty = t.baseQty().amount();
        e.quoteAmount = t.quoteAmount().amount();
        e.buyerUserId = t.buyerUserId();
        e.sellerUserId = t.sellerUserId();
        e.settledAt = Instant.now();
        em.persist(e);

        LOG.debugf("settled trade %s on %s: %s for %s", t.tradeId(), t.pair(), t.baseQty(), t.quoteAmount());
        tradeSettled.fire(new TradeSettled(t.tradeId(), t.pair(), t.baseQty(), t.quoteAmount(),
                t.buyerUserId(), t.sellerUserId()));
    }
}
