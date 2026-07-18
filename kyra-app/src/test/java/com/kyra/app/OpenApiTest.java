package com.kyra.app;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/** The public REST surface is documented via an OpenAPI spec (phase-3). */
@QuarkusTest
class OpenApiTest {

    @Test
    void openApiSpecIsPublishedAndDescribesEndpoints() {
        given().accept("application/json")
                .when().get("/q/openapi?format=json")
                .then().statusCode(200)
                .body(containsString("/v1/orders"))
                .body(containsString("/v1/market/ticker"));
    }
}
