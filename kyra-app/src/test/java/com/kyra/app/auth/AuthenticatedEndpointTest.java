package com.kyra.app.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Verifies the authenticated side of the auth API: with a valid JWT the
 * protected endpoints resolve the subject and return 200 (complementing
 * {@link AuthResourceTest}, which covers the 401 path).
 */
@QuarkusTest
class AuthenticatedEndpointTest {

    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_ID))
    void sessionsReturnsEmptyListForAuthenticatedUser() {
        given()
                .when().get("/v1/auth/sessions")
                .then().statusCode(200).body("size()", is(0));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_ID))
    void apiKeyListReturnsEmptyForAuthenticatedUser() {
        given()
                .when().get("/v1/auth/api-keys")
                .then().statusCode(200).body("size()", is(0));
    }
}
