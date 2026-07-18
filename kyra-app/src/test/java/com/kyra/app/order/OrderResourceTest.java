package com.kyra.app.order;

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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderResourceTest {

    // must be a ULID — it's used as the ledger user id
    private static final String USER = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    private static final AssetId ORD = AssetId.of("ORD");
    private static final AssetId USDO = AssetId.of("USDO");
    private static final PairSymbol PAIR = PairSymbol.parse("ORD-USDO");

    @Inject
    MarketApi market;

    @Inject
    AccountApi ledger;

    @BeforeEach
    void seed() {
        if (market.pair(PAIR).isEmpty()) {
            market.registerAsset(new Asset(ORD, "Order Coin", 8, AssetStatus.ACTIVE, 3));
            market.registerAsset(new Asset(USDO, "USD Order", 6, AssetStatus.ACTIVE, 6));
            market.registerPair(new Pair(PAIR, bd("1"), bd("0.001"), bd("10"),
                    bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));
        }
        // fund the authenticated user once
        if (ledger.balanceOf(USER, USDO).available().isZero()) {
            Money m = Money.of(USDO, bd("100000"));
            ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                    EntryLine.of(AccountKey.external(USDO), m.negated()),
                    EntryLine.of(AccountKey.userMain(USER, USDO), m))));
        }
    }

    @Test
    @TestSecurity(user = USER)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    void placeRestingBuyThenCancel() {
        String clientId = "rest-" + Ids.newUlid();
        String orderId = given().contentType("application/json")
                .body("{\"pair\":\"ORD-USDO\",\"side\":\"BUY\",\"tif\":\"GTC\","
                        + "\"price\":\"40000\",\"qty\":\"1\",\"clientOrderId\":\"" + clientId + "\"}")
                .when().post("/v1/orders")
                .then().statusCode(200)
                .body("status", equalTo("OPEN"))
                .body("orderId", notNullValue())
                .extract().path("orderId");

        given().when().delete("/v1/orders/" + orderId).then().statusCode(204);
        given().when().get("/v1/orders/" + orderId).then().statusCode(200).body("status", equalTo("CANCELED"));
    }

    @Test
    @TestSecurity(user = USER)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    void offGridPriceReturns400WithErrorCode() {
        given().contentType("application/json")
                .body("{\"pair\":\"ORD-USDO\",\"side\":\"BUY\",\"tif\":\"GTC\","
                        + "\"price\":\"40000.5\",\"qty\":\"1\",\"clientOrderId\":\"" + Ids.newUlid() + "\"}")
                .when().post("/v1/orders")
                .then().statusCode(400)
                .body("code", equalTo("TICK_SIZE"))
                .body("errorId", notNullValue());
    }

    @Test
    void placingRequiresAuthentication() {
        given().contentType("application/json")
                .body("{\"pair\":\"ORD-USDO\",\"side\":\"BUY\",\"price\":\"40000\",\"qty\":\"1\"}")
                .when().post("/v1/orders").then().statusCode(401);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
