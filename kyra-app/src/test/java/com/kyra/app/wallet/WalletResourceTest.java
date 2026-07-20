package com.kyra.app.wallet;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Wiring test for the custody REST layer (kyra-doc/modules/08): the endpoints
 * reach {@link com.kyra.wallet.api.WalletApi} (mock custody in tests) and are
 * JWT-guarded. Full deposit/withdraw domain behaviour is covered by the wallet
 * module's own tests; here we prove the app actually exposes it.
 */
@QuarkusTest
class WalletResourceTest {

    private static final String USER = "01ARZ3NDEKTSV4RRFFQ69G5FAW";

    @Test
    @TestSecurity(user = USER)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    void depositAddressReturnsAnAddressForTheAuthenticatedUser() {
        given()
                .when().get("/v1/wallet/address?asset=USDT")
                .then()
                .statusCode(200)
                .body("asset", is("USDT"))
                .body("address", notNullValue());
    }

    @Test
    void depositAddressRequiresAuthentication() {
        given()
                .when().get("/v1/wallet/address?asset=USDT")
                .then().statusCode(401);
    }

    @Test
    void withdrawRequiresAuthentication() {
        given()
                .contentType("application/json")
                .body("{\"asset\":\"USDT\",\"amount\":\"10\",\"toAddress\":\"0xabc\"}")
                .when().post("/v1/wallet/withdraw")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = USER)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    void withdrawReachesDomainAndIsRejectedForAnUnfundedUnverifiedUser() {
        // A fresh user has no KYC / no balance -> the domain rejects with a 4xx
        // (not 401, not a 500) — proving the endpoint reaches WalletApi.
        given()
                .contentType("application/json")
                .body("{\"asset\":\"USDT\",\"amount\":\"10\",\"toAddress\":\"0xabc\"}")
                .when().post("/v1/wallet/withdraw")
                .then()
                .statusCode(anyOf(is(400), is(403), is(409), is(422)));
    }
}
