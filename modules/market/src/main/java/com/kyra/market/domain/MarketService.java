package com.kyra.market.domain;

import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.AssetStatusChanged;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.OrderValidation;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;
import com.kyra.market.api.PairStatusChanged;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Market registry with an in-memory cache (kyra-doc/modules/03). Lookups and
 * {@link #validate} are served from the cache and never touch the database;
 * writes update the database and the cache in the same transaction.
 */
@ApplicationScoped
public class MarketService implements MarketApi {

    private static final Logger LOG = Logger.getLogger(MarketService.class);

    private final EntityManager em;
    private final Event<PairStatusChanged> pairStatusEvent;
    private final Event<AssetStatusChanged> assetStatusEvent;

    private final ConcurrentMap<String, Asset> assetCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Pair> pairCache = new ConcurrentHashMap<>();

    public MarketService(EntityManager em, Event<PairStatusChanged> pairStatusEvent,
            Event<AssetStatusChanged> assetStatusEvent) {
        this.em = em;
        this.pairStatusEvent = pairStatusEvent;
        this.assetStatusEvent = assetStatusEvent;
    }

    @Transactional
    void loadCache(@Observes StartupEvent ev) {
        em.createQuery("from AssetEntity", AssetEntity.class).getResultList()
                .forEach(a -> assetCache.put(a.symbol, toAsset(a)));
        em.createQuery("from PairEntity", PairEntity.class).getResultList()
                .forEach(p -> pairCache.put(p.symbol, toPair(p)));
        LOG.infof("market cache loaded: %d assets, %d pairs", assetCache.size(), pairCache.size());
    }

    @Override
    @Transactional
    public Asset registerAsset(Asset asset) {
        if (assetCache.containsKey(asset.id().symbol()) || em.find(AssetEntity.class, asset.id().symbol()) != null) {
            throw new IllegalStateException("asset already exists: " + asset.id());
        }
        AssetEntity e = new AssetEntity();
        e.symbol = asset.id().symbol();
        e.name = asset.name();
        e.scale = (short) asset.scale();
        e.status = asset.status().name();
        e.minConfirmations = asset.minConfirmations();
        e.createdAt = Instant.now();
        em.persist(e);
        recordConfig("ASSET", e.symbol, "system", null, e.status);
        assetCache.put(e.symbol, asset);
        LOG.infof("asset registered: %s", asset.id());
        return asset;
    }

    @Override
    @Transactional
    public Pair registerPair(Pair pair) {
        String sym = pair.symbol().toString();
        if (pairCache.containsKey(sym) || em.find(PairEntity.class, sym) != null) {
            throw new IllegalStateException("pair already exists: " + sym);
        }
        requireAsset(pair.symbol().base());
        requireAsset(pair.symbol().quote());

        PairEntity e = new PairEntity();
        e.symbol = sym;
        e.baseAsset = pair.symbol().base().symbol();
        e.quoteAsset = pair.symbol().quote().symbol();
        e.tickSize = pair.tickSize();
        e.stepSize = pair.stepSize();
        e.minNotional = pair.minNotional();
        e.minQty = pair.minQty();
        e.maxQty = pair.maxQty();
        e.maxOpenOrders = pair.maxOpenOrders();
        e.status = pair.status().name();
        e.createdAt = Instant.now();
        em.persist(e);
        recordConfig("PAIR", sym, "system", null, e.status);
        pairCache.put(sym, pair);
        LOG.infof("pair registered: %s status=%s", sym, pair.status());
        return pair;
    }

    @Override
    @Transactional
    public Pair updatePairRules(Pair pair, String changedBy) {
        String sym = pair.symbol().toString();
        PairEntity e = em.find(PairEntity.class, sym);
        if (e == null) {
            throw new IllegalStateException("unknown pair: " + sym);
        }
        if (!PairStatus.valueOf(e.status).equals(PairStatus.HALT)) {
            // rules may only change while halted so no live order sits on the old grid
            throw new IllegalStateException("pair rules can only change while HALT: " + sym);
        }
        String old = describe(e);
        e.tickSize = pair.tickSize();
        e.stepSize = pair.stepSize();
        e.minNotional = pair.minNotional();
        e.minQty = pair.minQty();
        e.maxQty = pair.maxQty();
        e.maxOpenOrders = pair.maxOpenOrders();
        Pair updated = pair.withStatus(PairStatus.HALT);
        recordConfig("PAIR", sym, changedBy, old, describe(e));
        pairCache.put(sym, updated);
        LOG.warnf("pair rules updated while HALT: %s by=%s", sym, changedBy);
        return updated;
    }

    @Override
    public Optional<Asset> asset(AssetId id) {
        return Optional.ofNullable(assetCache.get(id.symbol()));
    }

    @Override
    public Optional<Pair> pair(PairSymbol symbol) {
        return Optional.ofNullable(pairCache.get(symbol.toString()));
    }

    @Override
    public List<Asset> assets() {
        return List.copyOf(assetCache.values());
    }

    @Override
    public List<Pair> pairs() {
        return List.copyOf(pairCache.values());
    }

    @Override
    @Transactional
    public void changePairStatus(PairSymbol symbol, PairStatus newStatus, String changedBy, String reason) {
        String sym = symbol.toString();
        PairEntity e = em.find(PairEntity.class, sym);
        if (e == null) {
            throw new IllegalStateException("unknown pair: " + sym);
        }
        PairStatus current = PairStatus.valueOf(e.status);
        if (current == newStatus) {
            return;
        }
        if (!current.canTransitionTo(newStatus)) {
            throw new IllegalStateException("illegal pair transition " + current + " -> " + newStatus + " for " + sym);
        }
        e.status = newStatus.name();
        recordConfig("PAIR_STATUS", sym, changedBy, current.name(), newStatus.name());
        pairCache.computeIfPresent(sym, (k, p) -> p.withStatus(newStatus));
        LOG.warnf("pair status changed: %s %s -> %s by=%s reason=%s", sym, current, newStatus, changedBy, reason);
        pairStatusEvent.fire(new PairStatusChanged(symbol, current, newStatus, reason));
    }

    @Override
    @Transactional
    public void changeAssetStatus(AssetId id, AssetStatus newStatus, String changedBy) {
        AssetEntity e = em.find(AssetEntity.class, id.symbol());
        if (e == null) {
            throw new IllegalStateException("unknown asset: " + id);
        }
        AssetStatus current = AssetStatus.valueOf(e.status);
        if (current == newStatus) {
            return;
        }
        e.status = newStatus.name();
        recordConfig("ASSET_STATUS", id.symbol(), changedBy, current.name(), newStatus.name());
        Asset updated = new Asset(id, e.name, e.scale, newStatus, e.minConfirmations);
        assetCache.put(id.symbol(), updated);
        LOG.warnf("asset status changed: %s %s -> %s by=%s", id, current, newStatus, changedBy);
        assetStatusEvent.fire(new AssetStatusChanged(id, current, newStatus));

        // Freezing an asset halts every ACTIVE pair that uses it (kyra-doc/modules/03).
        if (newStatus == AssetStatus.FROZEN) {
            haltPairsUsing(id, changedBy);
        }
    }

    @Override
    public OrderValidation validate(PairSymbol symbol, BigDecimal price, BigDecimal qty) {
        Pair pair = pairCache.get(symbol.toString());
        if (pair == null) {
            return OrderValidation.rejected(OrderValidation.Error.PAIR_UNKNOWN);
        }
        if (pair.status() != PairStatus.ACTIVE) {
            return OrderValidation.rejected(OrderValidation.Error.PAIR_NOT_ACTIVE);
        }
        if (!onGrid(price, pair.tickSize())) {
            return OrderValidation.rejected(OrderValidation.Error.TICK_SIZE);
        }
        if (!onGrid(qty, pair.stepSize())) {
            return OrderValidation.rejected(OrderValidation.Error.STEP_SIZE);
        }
        if (qty.compareTo(pair.minQty()) < 0) {
            return OrderValidation.rejected(OrderValidation.Error.MIN_QTY);
        }
        if (qty.compareTo(pair.maxQty()) > 0) {
            return OrderValidation.rejected(OrderValidation.Error.MAX_QTY);
        }
        if (price.multiply(qty).compareTo(pair.minNotional()) < 0) {
            return OrderValidation.rejected(OrderValidation.Error.MIN_NOTIONAL);
        }
        return OrderValidation.OK;
    }

    // ----- helpers -----

    private void haltPairsUsing(AssetId asset, String changedBy) {
        String sym = asset.symbol();
        em.createQuery("from PairEntity where (baseAsset = :a or quoteAsset = :a) and status = :s", PairEntity.class)
                .setParameter("a", sym)
                .setParameter("s", PairStatus.ACTIVE.name())
                .getResultList()
                .forEach(p -> changePairStatus(PairSymbol.parse(p.symbol), PairStatus.HALT, changedBy,
                        "asset " + sym + " frozen"));
    }

    private static boolean onGrid(BigDecimal value, BigDecimal increment) {
        return value != null && value.signum() > 0 && value.remainder(increment).signum() == 0;
    }

    private void requireAsset(AssetId id) {
        if (em.find(AssetEntity.class, id.symbol()) == null) {
            throw new IllegalStateException("unknown asset: " + id);
        }
    }

    private void recordConfig(String type, String id, String changedBy, String oldValue, String newValue) {
        ConfigHistoryEntity h = new ConfigHistoryEntity();
        h.id = Ids.newUlid();
        h.entityType = type;
        h.entityId = id;
        h.changedBy = changedBy;
        h.oldValue = oldValue;
        h.newValue = newValue;
        h.at = Instant.now();
        em.persist(h);
    }

    private static String describe(PairEntity e) {
        return "tick=%s step=%s minNotional=%s minQty=%s maxQty=%s maxOpen=%d"
                .formatted(e.tickSize, e.stepSize, e.minNotional, e.minQty, e.maxQty, e.maxOpenOrders);
    }

    private static Asset toAsset(AssetEntity e) {
        return new Asset(AssetId.of(e.symbol), e.name, e.scale, AssetStatus.valueOf(e.status), e.minConfirmations);
    }

    private static Pair toPair(PairEntity e) {
        return new Pair(PairSymbol.parse(e.symbol), e.tickSize, e.stepSize, e.minNotional,
                e.minQty, e.maxQty, e.maxOpenOrders, PairStatus.valueOf(e.status));
    }
}
