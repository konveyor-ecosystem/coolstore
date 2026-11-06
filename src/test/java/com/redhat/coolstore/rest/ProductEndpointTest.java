package com.redhat.coolstore.rest;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Contract tests for /services/products against the Flyway V1_2 seeded
 * catalog (H2 in PostgreSQL mode under the %test profile).
 */
@QuarkusTest
class ProductEndpointTest {

    @Test
    @DisplayName("GET /services/products -> 200, non-empty JSON array (Flyway V1_2 seed)")
    void listAll_returnsSeededCatalog() {
        given()
                .when().get("/services/products")
                .then()
                .statusCode(200)
                .body("$", instanceOf(List.class))
                .body("size()", greaterThan(0));
    }

    @Test
    @DisplayName("GET /services/products/329299 -> 200 with the seeded catalog item")
    void getProduct_returnsSeededItem329299() {
        // 329299 is seeded by V1_2__AddInitialData.sql:
        // ('329299', 'Quarkus T-shirt', '', 10.00) with inventory Raleigh/736
        given()
                .when().get("/services/products/329299")
                .then()
                .statusCode(200)
                .body("itemId", equalTo("329299"))
                .body("name", equalTo("Quarkus T-shirt"))
                .body("price", equalTo(10.0f))
                .body("location", equalTo("Raleigh"));
    }
}
