package com.kyra.derivatives.domain;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.derivatives.api.MarkPriceProvider;
import com.kyra.derivatives.api.PerpetualApi;
import com.kyra.derivatives.api.Position;
import com.kyra.derivatives.api.PositionSide;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Perpetual futures (kyra-doc/modules/09 Part B). Positions lock margin
 * collateral in the ledger; PnL and liquidation are marked to the external mark
 * price (never the last internal trade). Settlement is one balanced journal:
 * profit is paid by the exchange perp account, loss is taken from margin, and
 * the insurance fund covers any shortfall so a user's balance never goes
 * negative. Funding, position averaging, and ADL are follow-ups (TECHDEBT).
 */
@ApplicationScoped
public class PerpetualService implements PerpetualApi {

    private static final Logger LOG = Logger.getLogger(PerpetualService.class);

    private final EntityManager em;
    private final AccountApi ledger;
    private final MarkPriceProvider markPrices;
    private final BigDecimal maintenanceMarginRate;

    public PerpetualService(EntityManager em, AccountApi ledger, MarkPriceProvider markPrices,
            @ConfigProperty(name = "kyra.perp.maintenance-margin-rate", defaultValue = "0.005")
            BigDecimal maintenanceMarginRate) {
        this.em = em;
        this.ledger = ledger;
        this.markPrices = markPrices;
        this.maintenanceMarginRate = maintenanceMarginRate;
    }

    @Override
    @Transactional
    public String openPosition(String userId, String symbol, PositionSide side,
            BigDecimal size, BigDecimal entryPrice, Money margin) {
        if (size == null || size.signum() <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        margin.requireNonNegative();

        // Adding to an existing same-side position averages the entry price and
        // adds size + margin, rather than opening a second position.
        PositionEntity open = findOpen(userId, symbol, side);
        if (open != null) {
            lockMargin(userId, margin, "perp-add:" + open.id + ":" + Ids.newUlid());
            BigDecimal newSize = open.size.add(size);
            BigDecimal avgEntry = open.entryPrice.multiply(open.size).add(entryPrice.multiply(size))
                    .divide(newSize, 18, java.math.RoundingMode.HALF_UP);
            BigDecimal newMargin = open.margin.add(margin.amount());
            em.createQuery("update PositionEntity set size = :sz, entryPrice = :ep, margin = :mg where id = :id")
                    .setParameter("sz", newSize).setParameter("ep", avgEntry)
                    .setParameter("mg", newMargin).setParameter("id", open.id)
                    .executeUpdate();
            LOG.infof("perp increased: id=%s new size=%s avg entry=%s", open.id, newSize, avgEntry);
            return open.id;
        }

        String id = Ids.newUlid();
        lockMargin(userId, margin, "perp-open:" + id);

        PositionEntity p = new PositionEntity();
        p.id = id;
        p.userId = userId;
        p.symbol = symbol;
        p.side = side.name();
        p.size = size;
        p.entryPrice = entryPrice;
        p.margin = margin.amount();
        p.collateralAsset = margin.asset().symbol();
        p.status = "OPEN";
        p.openedAt = Instant.now();
        em.persist(p);
        LOG.infof("perp opened: id=%s user=%s %s %s @ %s margin=%s", id, userId, side, symbol, entryPrice, margin);
        return id;
    }

    @Override
    @Transactional
    public void closePosition(String positionId) {
        PositionEntity p = openPositionOrNull(positionId);
        if (p == null) {
            return;
        }
        settle(p, markOf(p.symbol), "CLOSED");
    }

    @Override
    @Transactional
    public boolean liquidateIfUnderwater(String positionId) {
        PositionEntity p = openPositionOrNull(positionId);
        if (p == null) {
            return false;
        }
        BigDecimal mark = markOf(p.symbol);
        BigDecimal equity = toView(p).equity(mark);
        BigDecimal maintenance = p.size.multiply(mark).multiply(maintenanceMarginRate);
        if (equity.compareTo(maintenance) > 0) {
            return false; // healthy
        }
        settle(p, mark, "LIQUIDATED");
        LOG.warnf("perp liquidated: id=%s equity=%s <= maintenance=%s", positionId, equity, maintenance);
        return true;
    }

    @Override
    @Transactional
    public int applyFunding(String symbol, BigDecimal fundingRate, String roundId) {
        BigDecimal mark = markOf(symbol);
        List<PositionEntity> positions = em.createQuery(
                        "from PositionEntity where symbol = :s and status = 'OPEN'", PositionEntity.class)
                .setParameter("s", symbol).getResultList();

        int funded = 0;
        for (PositionEntity p : positions) {
            if (roundId.equals(p.lastFundingRound)) {
                continue; // this round already applied to this position
            }
            AssetId c = AssetId.of(p.collateralAsset);
            // positive payment = this position PAYS (long when rate>0); negative = receives
            BigDecimal notional = p.size.multiply(mark);
            BigDecimal payment = notional.multiply(fundingRate)
                    .multiply(BigDecimal.valueOf(PositionSide.valueOf(p.side).sign()));
            if (payment.signum() == 0) {
                continue;
            }
            // margin -= payment, kyra:perp += payment (net zero across a balanced book)
            Money pay = Money.of(c, payment.abs());
            List<EntryLine> lines = payment.signum() > 0
                    ? List.of(EntryLine.of(AccountKey.userMargin(p.userId, c), pay.negated()),
                              EntryLine.of(AccountKey.perp(c), pay))
                    : List.of(EntryLine.of(AccountKey.userMargin(p.userId, c), pay),
                              EntryLine.of(AccountKey.perp(c), pay.negated()));
            ledger.post(new JournalRequest(JournalType.PERP_FUNDING, "perp-funding:" + roundId + ":" + p.id, lines));
            p.margin = p.margin.subtract(payment);
            p.lastFundingRound = roundId;
            funded++;
        }
        LOG.infof("funding round %s on %s (rate %s): %d positions", roundId, symbol, fundingRate, funded);
        return funded;
    }

    @Override
    @Transactional
    public Optional<Position> position(String positionId) {
        PositionEntity p = em.find(PositionEntity.class, positionId);
        return (p == null || !"OPEN".equals(p.status)) ? Optional.empty() : Optional.of(toView(p));
    }

    // ----- settlement -----

    private void settle(PositionEntity p, BigDecimal mark, String endStatus) {
        AssetId c = AssetId.of(p.collateralAsset);
        BigDecimal margin = p.margin;
        BigDecimal pnl = toView(p).unrealizedPnl(mark);
        BigDecimal netToUser = margin.add(pnl);
        BigDecimal userMainDelta = netToUser.max(BigDecimal.ZERO);
        BigDecimal shortfall = netToUser.signum() < 0 ? netToUser.negate() : BigDecimal.ZERO;

        // Balanced per collateral asset:
        //   margin account releases -margin
        //   user main receives +max(margin+pnl, 0)
        //   perp account pays -pnl (profit out / loss in)
        //   insurance covers -shortfall (loss beyond margin)
        List<EntryLine> lines = new ArrayList<>();
        lines.add(EntryLine.of(AccountKey.userMargin(p.userId, c), Money.of(c, margin).negated()));
        if (userMainDelta.signum() > 0) {
            lines.add(EntryLine.of(AccountKey.userMain(p.userId, c), Money.of(c, userMainDelta)));
        }
        if (pnl.signum() != 0) {
            lines.add(EntryLine.of(AccountKey.perp(c), Money.of(c, pnl).negated()));
        }
        if (shortfall.signum() > 0) {
            lines.add(EntryLine.of(AccountKey.insurance(c), Money.of(c, shortfall).negated()));
        }
        ledger.post(new JournalRequest(JournalType.PERP_SETTLEMENT, "perp-close:" + p.id, lines));

        p.status = endStatus;
        p.realizedPnl = pnl;
        p.closedAt = Instant.now();
    }

    private void lockMargin(String userId, Money margin, String ref) {
        ledger.post(new JournalRequest(JournalType.PERP_MARGIN, ref, List.of(
                EntryLine.of(AccountKey.userMain(userId, margin.asset()), margin.negated()),
                EntryLine.of(AccountKey.userMargin(userId, margin.asset()), margin))));
    }

    private PositionEntity findOpen(String userId, String symbol, PositionSide side) {
        // lock the row so concurrent adds to the same position serialize (no lost read-modify-write)
        List<PositionEntity> found = em.createQuery(
                        "from PositionEntity where userId = :u and symbol = :s and side = :d and status = 'OPEN'",
                        PositionEntity.class)
                .setParameter("u", userId).setParameter("s", symbol).setParameter("d", side.name())
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1).getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    private BigDecimal markOf(String symbol) {
        return markPrices.markPrice(symbol)
                .orElseThrow(() -> new IllegalStateException("no mark price for " + symbol));
    }

    private PositionEntity openPositionOrNull(String positionId) {
        PositionEntity p = em.find(PositionEntity.class, positionId);
        return (p == null || !"OPEN".equals(p.status)) ? null : p;
    }

    private static Position toView(PositionEntity p) {
        return new Position(p.id, p.userId, p.symbol, PositionSide.valueOf(p.side),
                p.size, p.entryPrice, p.margin, AssetId.of(p.collateralAsset));
    }
}
