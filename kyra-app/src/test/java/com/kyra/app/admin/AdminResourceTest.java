package com.kyra.app.admin;

import com.kyra.common.id.Ids;
import com.kyra.compliance.api.ComplianceApi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AdminResourceTest {

    private static final String ADMIN = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @Inject
    ComplianceApi compliance;

    @Test
    @TestSecurity(user = ADMIN, roles = {"ADMIN"})
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN))
    void adminCanFreezeAndUnfreezeUser() {
        String user = Ids.newUlid();
        given().contentType("application/json").body("{\"reason\":\"review\"}")
                .when().post("/v1/admin/users/" + user + "/freeze")
                .then().statusCode(204);
        assertTrue(compliance.isFrozen(user));

        given().contentType("application/json")
                .when().post("/v1/admin/users/" + user + "/unfreeze")
                .then().statusCode(204);
        assertFalse(compliance.isFrozen(user));
    }

    @Test
    @TestSecurity(user = "01ARZ3NDEKTSV4RRFFQ69G5FAW", roles = {"USER"})
    @JwtSecurity(claims = @Claim(key = "sub", value = "01ARZ3NDEKTSV4RRFFQ69G5FAW"))
    void nonAdminIsForbidden() {
        given().contentType("application/json").body("{\"reason\":\"x\"}")
                .when().post("/v1/admin/users/" + Ids.newUlid() + "/freeze")
                .then().statusCode(403);
    }

    @Test
    void unauthenticatedIsRejected() {
        given().contentType("application/json").body("{}")
                .when().post("/v1/admin/users/" + Ids.newUlid() + "/freeze")
                .then().statusCode(401);
    }
}
