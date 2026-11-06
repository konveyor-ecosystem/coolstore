package com.redhat.coolstore.rest;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Contract test for /services/orders. An empty array is a valid response:
 * orders are only persisted by the orders-topic consumer after a checkout
 * with a live broker, which the %test profile disables.
 */
@QuarkusTest
class OrderEndpointTest {

    @Test
    @DisplayName("GET /services/orders -> 200 with a JSON array (empty is OK)")
    void listAll_returnsOkArray() {
        given()
                .when().get("/services/orders")
                .then()
                .statusCode(200)
                .body("$", instanceOf(List.class));
    }
}
