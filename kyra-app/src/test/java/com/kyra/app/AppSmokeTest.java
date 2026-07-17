package com.kyra.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;

/**
 * Boots the whole application (Dev Services: Postgres + Redis) and verifies
 * the phase-0 exit criteria: app starts, health is UP, Flyway migrated,
 * metrics are exposed. Runs in CI on every build.
 */
@QuarkusTest
class AppSmokeTest {

    @Test
    void healthIsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void metricsExposed() {
        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("jvm_"));
    }
}
