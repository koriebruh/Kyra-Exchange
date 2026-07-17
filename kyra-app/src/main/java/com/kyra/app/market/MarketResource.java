package com.kyra.app.market;

import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Public market reference data (kyra-doc/modules/03). Read-only and unauthenticated;
 * registry mutations happen through the admin module.
 */
@Path("/v1/market")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class MarketResource {

    private final MarketApi market;

    public MarketResource(MarketApi market) {
        this.market = market;
    }

    @GET
    @Path("/assets")
    public List<Asset> assets() {
        return market.assets();
    }

    @GET
    @Path("/pairs")
    public List<Pair> pairs() {
        return market.pairs();
    }

    @GET
    @Path("/pairs/{symbol}")
    public Pair pair(@PathParam("symbol") String symbol) {
        return market.pair(PairSymbol.parse(symbol))
                .orElseThrow(() -> new NotFoundException("unknown pair: " + symbol));
    }
}
