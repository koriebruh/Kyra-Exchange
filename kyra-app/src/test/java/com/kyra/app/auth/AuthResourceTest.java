package com.kyra.app.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Wire-contract tests for the auth endpoints: anti-enumeration on register,
 * uniform {@link com.kyra.app.error.ApiError} shape on failure, and auth guards.
 */
@QuarkusTest
class AuthResourceTest {

    private String uniqueEmail() {
        return "http-" + UUID.randomUUID() + "@kyra.test";
    }

    @Test
    void registerReturnsUniformAcceptedResponse() {
        String email = uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"supersecret-1\"}";

        given().contentType("application/json").body(body)
                .when().post("/v1/auth/register")
                .then().statusCode(202).body("message", notNullValue());

        // duplicate registration must look identical (no account enumeration)
        given().contentType("application/json").body(body)
                .when().post("/v1/auth/register")
                .then().statusCode(202).body("message", notNullValue());
    }

    @Test
    void invalidEmailReturns400WithErrorShape() {
        given().contentType("application/json")
                .body("{\"email\":\"not-an-email\",\"password\":\"supersecret-1\"}")
                .when().post("/v1/auth/register")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("errorId", notNullValue());
    }

    @Test
    void loginBeforeVerificationReturns401() {
        String email = uniqueEmail();
        given().contentType("application/json")
                .body("{\"email\":\"" + email + "\",\"password\":\"supersecret-1\"}")
                .when().post("/v1/auth/register").then().statusCode(202);

        given().contentType("application/json")
                .body("{\"email\":\"" + email + "\",\"password\":\"supersecret-1\"}")
                .when().post("/v1/auth/login")
                .then().statusCode(401)
                .body("code", equalTo("AUTHENTICATION_FAILED"));
    }

    @Test
    void sessionsRequireAuthentication() {
        given().when().get("/v1/auth/sessions").then().statusCode(401);
    }
}
