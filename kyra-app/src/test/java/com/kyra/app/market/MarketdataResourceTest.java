package com.kyra.app.market;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.PlaceOrder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end: a real trade flows through settlement -> the market-data observer,
 * and the public ticker/candles endpoints reflect it.
 */
@QuarkusTest
class MarketdataResourceTest {

    @Inject
    MarketApi market;

    @Inject
    AccountApi ledger;

    @Inject
    OrderApi orders;

    @Test
    void tradeShowsUpInTickerAndCandles() {
        AssetId base = AssetId.of("MDX");
        AssetId quote = AssetId.of("MDQ");
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerAsset(new Asset(base, "MD Base", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "MD Quote", 6, AssetStatus.ACTIVE, 6));
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));

        String seller = fund(base, "1");
        String buyer = fund(quote, "100000");
        orders.place(new PlaceOrder(seller, pair, OrderSide.SELL, TimeInForce.GTC, bd("50000"), bd("1"), Ids.newUlid()));
        orders.place(new PlaceOrder(buyer, pair, OrderSide.BUY, TimeInForce.GTC, bd("50000"), bd("1"), Ids.newUlid()));

        given().when().get("/v1/market/ticker?pair=MDX-MDQ")
                .then().statusCode(200)
                .body("lastPrice", equalTo(50000.0f))
                .body("volumeBase24h", equalTo(1.0f));

        given().when().get("/v1/market/candles?pair=MDX-MDQ&interval=1m&limit=10")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void tickerForUntradedPairReturns404() {
        given().when().get("/v1/market/ticker?pair=NO-PE").then().statusCode(404);
    }

    @Test
    void depthReflectsRestingOrders() {
        AssetId base = AssetId.of("DPX");
        AssetId quote = AssetId.of("DPQ");
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerAsset(new Asset(base, "Depth Base", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "Depth Quote", 6, AssetStatus.ACTIVE, 6));
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));

        String seller = fund(base, "1");
        String buyer = fund(quote, "100000");
        // no cross: sell above, buy below -> both rest
        orders.place(new PlaceOrder(seller, pair, OrderSide.SELL, TimeInForce.GTC, bd("50000"), bd("1"), Ids.newUlid()));
        orders.place(new PlaceOrder(buyer, pair, OrderSide.BUY, TimeInForce.GTC, bd("49000"), bd("1"), Ids.newUlid()));

        var json = given().when().get("/v1/market/depth?pair=DPX-DPQ&limit=10")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(0, bd(json.getString("bids[0].price")).compareTo(bd("49000")));
        assertEquals(0, bd(json.getString("bids[0].qty")).compareTo(bd("1")));
        assertEquals(0, bd(json.getString("asks[0].price")).compareTo(bd("50000")));
        assertEquals(0, bd(json.getString("asks[0].qty")).compareTo(bd("1")));
    }

    private String fund(AssetId asset, String amount) {
        String user = Ids.newUlid();
        Money m = Money.of(asset, bd(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
        return user;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
