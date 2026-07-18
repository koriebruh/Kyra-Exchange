package com.kyra.app.market;

import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.marketdata.api.Candle;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;
import com.kyra.matching.api.DepthSnapshot;
import com.kyra.matching.api.MatchingEngineApi;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public market data (kyra-doc/modules/07): candles and 24h ticker. No auth,
 * no user-identifying data.
 */
@Path("/v1/market")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class MarketdataResource {

    private final MarketdataApi marketdata;
    private final MatchingEngineApi engine;
    private final MarketApi market;

    public MarketdataResource(MarketdataApi marketdata, MatchingEngineApi engine, MarketApi market) {
        this.marketdata = marketdata;
        this.engine = engine;
        this.market = market;
    }

    public record DepthLevel(BigDecimal price, BigDecimal qty) {
    }

    public record DepthResponse(String pair, List<DepthLevel> bids, List<DepthLevel> asks) {
    }

    @GET
    @Path("/candles")
    public List<Candle> candles(@QueryParam("pair") String pair,
            @QueryParam("interval") String interval,
            @QueryParam("limit") Integer limit) {
        return marketdata.candles(PairSymbol.parse(pair),
                interval == null ? "1m" : interval,
                limit == null ? 500 : limit);
    }

    @GET
    @Path("/ticker")
    public Ticker ticker(@QueryParam("pair") String pair) {
        return marketdata.ticker(PairSymbol.parse(pair))
                .orElseThrow(() -> new NotFoundException("no market data for pair: " + pair));
    }

    @GET
    @Path("/depth")
    public DepthResponse depth(@QueryParam("pair") String pair, @QueryParam("limit") Integer limit) {
        PairSymbol symbol = PairSymbol.parse(pair);
        Pair p = market.pair(symbol).orElseThrow(() -> new NotFoundException("unknown pair: " + pair));
        DepthSnapshot snap = engine.depth(symbol, limit == null ? 50 : limit);
        return new DepthResponse(pair,
                snap.bids().stream().map(l -> toLevel(p, l)).toList(),
                snap.asks().stream().map(l -> toLevel(p, l)).toList());
    }

    private static DepthLevel toLevel(Pair p, DepthSnapshot.Level l) {
        return new DepthLevel(
                p.tickSize().multiply(BigDecimal.valueOf(l.priceTicks())),
                p.stepSize().multiply(BigDecimal.valueOf(l.qtySteps())));
    }
}
