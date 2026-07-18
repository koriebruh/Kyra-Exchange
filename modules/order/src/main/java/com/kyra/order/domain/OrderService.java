package com.kyra.order.domain;

import com.kyra.account.api.AccountApi;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.fee.api.FeeApi;
import com.kyra.fee.api.FeeRates;
import com.kyra.fee.api.Fees;
import com.kyra.market.api.Asset;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.OrderValidation;
import com.kyra.market.api.Pair;
import com.kyra.matching.api.EngineOrderType;
import com.kyra.matching.api.MatchEvent;
import com.kyra.matching.api.MatchingEngineApi;
import com.kyra.matching.api.OrderSide;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.OrderRejectedException;
import com.kyra.order.api.OrderStatus;
import com.kyra.order.api.OrderView;
import com.kyra.order.api.PlaceOrder;
import com.kyra.risk.api.RiskApi;
import com.kyra.risk.api.RiskDecision;
import com.kyra.settlement.api.SettlementApi;
import com.kyra.settlement.api.TradeSettlement;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Order intake and lifecycle (kyra-doc/modules/04). Each pair is processed by a
 * single writer: placement and cancellation acquire the pair's lock and run the
 * whole flow — validate, hold, match, settle, update state, release — inside one
 * transaction. Funds are always held before an order reaches the book, and the
 * unfilled/over-held remainder is released when the order terminates.
 */
@ApplicationScoped
public class OrderService implements OrderApi {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    private final EntityManager em;
    private final MarketApi market;
    private final AccountApi ledger;
    private final MatchingEngineApi engine;
    private final SettlementApi settlement;
    private final FeeApi fees;
    private final RiskApi risk;

    private final ConcurrentMap<String, Object> pairLocks = new ConcurrentHashMap<>();

    public OrderService(EntityManager em, MarketApi market, AccountApi ledger,
            MatchingEngineApi engine, SettlementApi settlement, FeeApi fees, RiskApi risk) {
        this.em = em;
        this.market = market;
        this.ledger = ledger;
        this.engine = engine;
        this.settlement = settlement;
        this.fees = fees;
        this.risk = risk;
    }

    @Override
    public OrderView place(PlaceOrder cmd) {
        // single writer per pair: hold the lock across the whole transaction
        synchronized (lockFor(cmd.pair())) {
            return QuarkusTransaction.requiringNew().call(() -> doPlace(cmd));
        }
    }

    @Override
    public void cancel(String userId, String orderId) {
        OrderEntity order = QuarkusTransaction.requiringNew().call(() -> em.find(OrderEntity.class, orderId));
        if (order == null || !order.userId.equals(userId)) {
            return; // unknown or not the owner — nothing to do (idempotent)
        }
        synchronized (lockFor(PairSymbol.parse(order.pair))) {
            QuarkusTransaction.requiringNew().run(() -> doCancel(userId, orderId));
        }
    }

    @Override
    public Optional<OrderView> get(String userId, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            OrderEntity o = em.find(OrderEntity.class, orderId);
            return (o == null || !o.userId.equals(userId)) ? Optional.<OrderView>empty() : Optional.of(view(o));
        });
    }

    @Override
    public List<OrderView> openOrders(String userId, PairSymbol pair) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("from OrderEntity where userId = :u and pair = :p "
                                + "and status in ('OPEN','PARTIALLY_FILLED') order by createdAt", OrderEntity.class)
                        .setParameter("u", userId)
                        .setParameter("p", pair.toString())
                        .getResultList().stream().map(OrderService::view).toList());
    }

    // ----- placement -----

    private OrderView doPlace(PlaceOrder cmd) {
        // Idempotency: re-submitting the same client_order_id returns the original
        // order unchanged (kyra-doc/modules/04), never places a second one.
        if (cmd.clientOrderId() != null) {
            OrderEntity existing = findByClientOrderId(cmd.userId(), cmd.clientOrderId());
            if (existing != null) {
                return view(existing);
            }
        }

        Pair pair = market.pair(cmd.pair())
                .orElseThrow(() -> new OrderRejectedException("PAIR_UNKNOWN", "unknown pair: " + cmd.pair()));

        OrderValidation validation = market.validate(cmd.pair(), cmd.price(), cmd.qty());
        if (!validation.valid()) {
            throw new OrderRejectedException(validation.error().name(),
                    "order rejected: " + validation.error());
        }

        BigDecimal notional = cmd.price().multiply(cmd.qty());
        RiskDecision riskDecision = risk.checkOrder(cmd.userId(), cmd.pair(), cmd.price(), notional);
        if (!riskDecision.allowed()) {
            throw new OrderRejectedException(riskDecision.reason(), "order rejected by risk: " + riskDecision.reason());
        }

        AssetId base = cmd.pair().base();
        AssetId quote = cmd.pair().quote();
        Money holdAmount = cmd.side() == OrderSide.BUY
                ? Money.of(quote, cmd.price().multiply(cmd.qty()))
                : Money.of(base, cmd.qty());

        OrderEntity order = persistAccepted(cmd, holdAmount);
        // hold funds before the order can match; throws InsufficientBalanceException if short
        ledger.hold(cmd.userId(), holdAmount, "order:" + order.id);

        long priceTicks = toTicks(cmd.price(), pair.tickSize());
        long qtySteps = toTicks(cmd.qty(), pair.stepSize());

        List<MatchEvent> events = engine.submit(cmd.pair(), order.id, cmd.userId(), cmd.side(),
                EngineOrderType.LIMIT, cmd.tif(), priceTicks, qtySteps);

        applyEvents(order, pair, events);
        finalizeTaker(order);
        LOG.debugf("order %s placed on %s: status=%s filled=%s", order.id, cmd.pair(), order.status, order.filledQty);
        return view(order);
    }

    private void applyEvents(OrderEntity taker, Pair pair, List<MatchEvent> events) {
        for (MatchEvent event : events) {
            switch (event) {
                case MatchEvent.TradeExecuted t -> settleTrade(taker, pair, t);
                case MatchEvent.OrderRested r -> {
                    taker.status = OrderStatus.OPEN.name();
                    taker.bookSeq = r.seq();
                }
                case MatchEvent.OrderExpired e -> { /* remainder handled in finalizeTaker */ }
                case MatchEvent.OrderCanceled c -> { /* not produced by submit */ }
            }
        }
    }

    private void settleTrade(OrderEntity taker, Pair pair, MatchEvent.TradeExecuted t) {
        BigDecimal tradeQty = pair.stepSize().multiply(BigDecimal.valueOf(t.qtySteps()));
        BigDecimal tradePrice = pair.tickSize().multiply(BigDecimal.valueOf(t.priceTicks()));
        Money baseQty = Money.of(pair.symbol().base(), tradeQty);
        Money quoteAmount = Money.of(pair.symbol().quote(), tradePrice.multiply(tradeQty));

        OrderEntity maker = em.find(OrderEntity.class, t.makerOrderId());
        String buyerUser;
        String sellerUser;
        OrderEntity buyOrder;
        OrderEntity sellOrder;
        if (t.takerSide() == OrderSide.BUY) {
            buyerUser = t.takerUserId();
            sellerUser = t.makerUserId();
            buyOrder = taker;
            sellOrder = maker;
        } else {
            buyerUser = t.makerUserId();
            sellerUser = t.takerUserId();
            buyOrder = maker;
            sellOrder = taker;
        }

        // Fee is taken from the asset each party receives, at that order's frozen
        // rate for its role (taker order pays taker rate, maker order maker rate).
        int baseScale = market.asset(pair.symbol().base()).map(Asset::scale).orElse(18);
        int quoteScale = market.asset(pair.symbol().quote()).map(Asset::scale).orElse(18);
        BigDecimal buyRate = (t.takerSide() == OrderSide.BUY) ? buyOrder.takerRate : buyOrder.makerRate;
        BigDecimal sellRate = (t.takerSide() == OrderSide.SELL) ? sellOrder.takerRate : sellOrder.makerRate;
        Money buyerFee = Fees.charge(baseQty, buyRate, baseScale);
        Money sellerFee = Fees.charge(quoteAmount, sellRate, quoteScale);

        settlement.settle(new TradeSettlement(Ids.newUlid(), pair.symbol(), buyerUser, sellerUser,
                baseQty, quoteAmount, buyerFee, sellerFee));

        applyFill(buyOrder, tradeQty, quoteAmount.amount()); // buyer consumes quote from hold
        applyFill(sellOrder, tradeQty, tradeQty);            // seller consumes base from hold
    }

    /** Advance an order by one fill; if fully filled, finalize and release any remainder. */
    private void applyFill(OrderEntity order, BigDecimal tradeQty, BigDecimal holdConsumed) {
        order.filledQty = order.filledQty.add(tradeQty);
        order.heldRemaining = order.heldRemaining.subtract(holdConsumed);
        order.updatedAt = Instant.now();
        if (order.filledQty.compareTo(order.qty) >= 0) {
            order.status = OrderStatus.FILLED.name();
            releaseRemainder(order);
        } else {
            order.status = OrderStatus.PARTIALLY_FILLED.name();
        }
    }

    private void finalizeTaker(OrderEntity taker) {
        OrderStatus status = OrderStatus.valueOf(taker.status);
        if (status.isTerminal() || status == OrderStatus.OPEN) {
            return; // FILLED already released; OPEN keeps its hold
        }
        // ACCEPTED/PARTIALLY_FILLED with no rest = IOC/FOK/STP remainder dropped
        taker.status = OrderStatus.EXPIRED.name();
        releaseRemainder(taker);
    }

    private void releaseRemainder(OrderEntity order) {
        if (order.heldRemaining.signum() > 0) {
            ledger.release(order.userId, Money.of(AssetId.of(order.holdAsset), order.heldRemaining),
                    "release:" + order.id);
            order.heldRemaining = BigDecimal.ZERO;
        }
        order.updatedAt = Instant.now();
    }

    // ----- cancellation -----

    private void doCancel(String userId, String orderId) {
        OrderEntity order = em.find(OrderEntity.class, orderId);
        if (order == null || !order.userId.equals(userId) || OrderStatus.valueOf(order.status).isTerminal()) {
            return;
        }
        engine.cancel(PairSymbol.parse(order.pair), orderId, "user");
        order.status = OrderStatus.CANCELED.name();
        releaseRemainder(order);
        LOG.debugf("order %s canceled", orderId);
    }

    // ----- helpers -----

    private OrderEntity persistAccepted(PlaceOrder cmd, Money holdAmount) {
        FeeRates rates = fees.ratesFor(cmd.userId(), cmd.pair());
        OrderEntity o = new OrderEntity();
        o.id = Ids.newUlid();
        o.userId = cmd.userId();
        o.clientOrderId = cmd.clientOrderId() == null ? o.id : cmd.clientOrderId();
        o.pair = cmd.pair().toString();
        o.side = cmd.side().name();
        o.price = cmd.price();
        o.qty = cmd.qty();
        o.filledQty = BigDecimal.ZERO;
        o.heldRemaining = holdAmount.amount();
        o.holdAsset = holdAmount.asset().symbol();
        o.status = OrderStatus.ACCEPTED.name();
        o.makerRate = rates.makerRate();
        o.takerRate = rates.takerRate();
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        em.persist(o);
        return o;
    }

    private OrderEntity findByClientOrderId(String userId, String clientOrderId) {
        try {
            return em.createQuery(
                            "from OrderEntity where userId = :u and clientOrderId = :c", OrderEntity.class)
                    .setParameter("u", userId).setParameter("c", clientOrderId).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private static long toTicks(BigDecimal value, BigDecimal increment) {
        return value.divideToIntegralValue(increment).longValueExact();
    }

    private Object lockFor(PairSymbol pair) {
        return pairLocks.computeIfAbsent(pair.toString(), k -> new Object());
    }

    private static OrderView view(OrderEntity o) {
        return new OrderView(o.id, o.clientOrderId, o.pair, OrderSide.valueOf(o.side),
                o.price, o.qty, o.filledQty, OrderStatus.valueOf(o.status), o.createdAt);
    }
}
