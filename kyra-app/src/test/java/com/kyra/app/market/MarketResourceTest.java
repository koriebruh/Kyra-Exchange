package com.kyra.app.market;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MarketResourceTest {

    @Inject
    MarketApi market;

    @BeforeEach
    void seed() {
        if (market.pair(PairSymbol.parse("MKT-USD")).isEmpty()) {
            market.registerAsset(new Asset(AssetId.of("MKT"), "Market Coin", 8, AssetStatus.ACTIVE, 3));
            market.registerAsset(new Asset(AssetId.of("USD"), "US Dollar", 2, AssetStatus.ACTIVE, 1));
            market.registerPair(new Pair(PairSymbol.parse("MKT-USD"),
                    new BigDecimal("0.01"), new BigDecimal("0.001"), new BigDecimal("5"),
                    new BigDecimal("0.001"), new BigDecimal("1000"), 100, PairStatus.ACTIVE));
        }
    }

    @Test
    void listsPairs() {
        given().when().get("/v1/market/pairs")
                .then().statusCode(200).body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    void getsSinglePair() {
        given().when().get("/v1/market/pairs/MKT-USD")
                .then().statusCode(200)
                .body("status", equalTo("ACTIVE"))
                .body("tickSize", notNullValue());
    }

    @Test
    void unknownPairReturns404WithErrorShape() {
        given().when().get("/v1/market/pairs/NO-PE")
                .then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("errorId", notNullValue());
    }
}
