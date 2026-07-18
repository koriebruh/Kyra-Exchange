package com.kyra.app.market;

import com.kyra.common.money.PairSymbol;
import com.kyra.marketdata.api.Candle;
import com.kyra.marketdata.api.MarketdataApi;
import com.kyra.marketdata.api.Ticker;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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

    public MarketdataResource(MarketdataApi marketdata) {
        this.marketdata = marketdata;
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
}
