package com.redhat.coolstore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test (run with -Pintegration-tests; needs Docker/Podman for PostgreSQL Dev Services).
 * Contract mirrors the EAP characterization baseline for all 9 REST operations under /services.
 */
@QuarkusTest
class RestContractIT {

    @Test
    void healthReady() {
        given().when().get("/q/health/ready").then().statusCode(200);
    }

    @Test
    void listProducts() {
        given().when().get("/services/products").then().statusCode(200)
            .body("itemId", hasItem("329299")).body("size()", greaterThanOrEqualTo(9));
    }

    @Test
    void getProduct() {
        given().when().get("/services/products/329299").then().statusCode(200)
            .body("itemId", equalTo("329299")).body("name", equalTo("Quarkus T-shirt"));
    }

    @Test
    void getCart() {
        given().when().get("/services/cart/c1").then().statusCode(200)
            .body("cartItemTotal", equalTo(0f));
    }

    @Test
    void addToCartPricesIt() {
        given().when().post("/services/cart/c1/329299/2").then().statusCode(200)
            .body("cartItemTotal", equalTo(15.0f)).body("shippingTotal", equalTo(2.99f))
            .body("cartTotal", equalTo(17.99f));
    }

    @Test
    void removeFromCart() {
        given().when().post("/services/cart/c2/329299/2");
        given().when().delete("/services/cart/c2/329299/1").then().statusCode(200)
            .body("cartItemTotal", equalTo(7.5f));
    }

    @Test
    void checkoutRequiresAuthentication() {
        given().when().post("/services/cart/checkout/c1").then().statusCode(401);
    }

    @Test
    void ordersRequireAuthentication() {
        given().when().get("/services/orders").then().statusCode(401);
    }
}
