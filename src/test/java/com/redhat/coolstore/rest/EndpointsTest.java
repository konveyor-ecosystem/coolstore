package com.redhat.coolstore.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** REST contract for the products, cart and orders endpoints (field names used by the AngularJS UI). */
@QuarkusTest
class EndpointsTest {

    @Test
    void productListAndSingleProduct() {
        given().get("/services/products").then().statusCode(200)
                .body("itemId", hasItem("329299"))
                .body("[0]", allOf(hasKey("itemId"), hasKey("name"), hasKey("desc"), hasKey("price"),
                        hasKey("location"), hasKey("quantity"), hasKey("link")));
        given().get("/services/products/329299").then().statusCode(200)
                .body("itemId", equalTo("329299")).body("name", equalTo("Quarkus T-shirt"))
                .body("price", equalTo(10.0f));
    }

    @Test
    void emptyCartShape() {
        given().get("/services/cart/ep-empty").then().statusCode(200)
                .body("shoppingCartItemList", empty())
                .body("$", allOf(hasKey("cartItemTotal"), hasKey("cartItemPromoSavings"), hasKey("shippingTotal"),
                        hasKey("shippingPromoSavings"), hasKey("cartTotal")))
                .body("cartTotal", equalTo(0.0f));
    }

    @Test
    void addPricesWithPromotionThenRemove() {
        given().post("/services/cart/ep-1/329299/3").then().statusCode(200)
                .body("cartItemTotal", equalTo(22.5f))
                .body("cartItemPromoSavings", equalTo(-7.5f))
                .body("shippingTotal", equalTo(2.99f))
                .body("cartTotal", equalTo(25.49f))
                .body("shoppingCartItemList[0].product.itemId", equalTo("329299"))
                .body("shoppingCartItemList[0].quantity", equalTo(3));
        given().delete("/services/cart/ep-1/329299/3").then().statusCode(200)
                .body("shoppingCartItemList", empty()).body("cartTotal", equalTo(0.0f));
    }

    @Test
    void setCopiesTmpCart() {
        given().post("/services/cart/ep-tmp/329199/1").then().statusCode(200);
        given().post("/services/cart/ep-main/ep-tmp").then().statusCode(200)
                .body("shoppingCartItemList[0].product.itemId", equalTo("329199"))
                .body("cartItemTotal", equalTo(9.0f));
    }

    @Test
    void checkoutPersistsOrderReadableById() {
        given().post("/services/cart/ep-co/165613/2").then().statusCode(200);
        given().post("/services/cart/checkout/ep-co").then().statusCode(200)
                .body("shoppingCartItemList", empty());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                given().get("/services/orders").then().statusCode(200)
                        .body("itemList.flatten().productId", hasItem("165613")));
        Number id = given().get("/services/orders").then().extract().path("[0].orderId");
        given().get("/services/orders/" + id).then().statusCode(200)
                .body("orderId", equalTo(id.intValue()))
                .body("$", allOf(hasKey("customerName"), hasKey("orderValue"), hasKey("itemList")));
    }

    @Test
    void healthLiveAndReadyUp() {
        given().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
        given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
    }
}
