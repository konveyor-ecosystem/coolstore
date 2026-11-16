package com.redhat.coolstore.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * @QuarkusTest integration tests for ProductEndpoint.
 * Uses @TestSecurity to bypass OIDC in test mode.
 */
@QuarkusTest
public class ProductEndpointTest {

    @Test
    @TestSecurity(authorizationEnabled = false)
    public void testListAllProducts() {
        given()
            .when().get("/services/products/")
            .then()
            .statusCode(200)
            .contentType("application/json");
    }

    @Test
    @TestSecurity(authorizationEnabled = false)
    public void testGetProduct_notFound() {
        given()
            .when().get("/services/products/nonexistent-sku")
            .then()
            .statusCode(anyOf(is(200), is(204), is(404)));
    }
}
