package com.kyra.app.ratelimit;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RateLimiterTest {

    @Inject
    RateLimiter limiter;

    @Test
    void allowsUpToTheLimitThenRejects() {
        String subject = "test-" + UUID.randomUUID();
        assertTrue(limiter.check(subject, 3, 60).allowed());
        assertTrue(limiter.check(subject, 3, 60).allowed());
        RateLimiter.Decision third = limiter.check(subject, 3, 60);
        assertTrue(third.allowed());
        assertEquals0(third.remaining());
        assertFalse(limiter.check(subject, 3, 60).allowed(), "4th request over the limit of 3");
    }

    @Test
    void separateSubjectsHaveSeparateBudgets() {
        String a = "a-" + UUID.randomUUID();
        String b = "b-" + UUID.randomUUID();
        assertTrue(limiter.check(a, 1, 60).allowed());
        assertFalse(limiter.check(a, 1, 60).allowed(), "a's budget of 1 is spent");
        assertTrue(limiter.check(b, 1, 60).allowed(), "b has its own fresh budget");
    }

    @Test
    void publicEndpointCarriesRateLimitHeaders() {
        given().when().get("/v1/market/pairs")
                .then().statusCode(200)
                .header("X-RateLimit-Limit", notNullValue())
                .header("X-RateLimit-Remaining", notNullValue());
    }

    private static void assertEquals0(long remaining) {
        org.junit.jupiter.api.Assertions.assertEquals(0, remaining);
    }
}
