package com.redhat.coolstore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RestContractTest {
    @Test
    void productsListed() {
        given().get("/services/products").then().statusCode(200).body("size()", greaterThan(0)).body("[0].itemId", notNullValue());
    }
    @Test
    void cartAddAndCheckoutCreatesOrder() {
        String id = given().get("/services/products").then().extract().path("[0].itemId");
        given().post("/services/cart/c1/" + id + "/2").then().statusCode(200).body("cartItemTotal", greaterThan(0f));
        given().post("/services/cart/checkout/c1").then().statusCode(200);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
            given().get("/services/orders").then().statusCode(200).body("size()", greaterThan(0)));
    }
    @Test
    void healthAndUi() {
        given().get("/q/health/ready").then().statusCode(200);
        given().get("/").then().statusCode(200);
    }
}
