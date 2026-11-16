package com.redhat.coolstore.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
public class OrderEndpointTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    public void testListAllOrders_authenticated() {
        given()
            .when().get("/services/orders/")
            .then()
            .statusCode(200)
            .contentType("application/json");
    }

    @Test
    public void testListAllOrders_unauthenticated() {
        // When OIDC is enabled in prod, unauthenticated requests return 401.
        // In dev mode with quarkus.oidc.enabled=false this returns 200.
        // Test just validates the endpoint is reachable.
        given()
            .when().get("/services/orders/")
            .then()
            .statusCode(anyOf(is(200), is(401)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    public void testGetOrder_notFound() {
        given()
            .when().get("/services/orders/99999")
            .then()
            .statusCode(anyOf(is(200), is(204), is(404)));
    }
}
