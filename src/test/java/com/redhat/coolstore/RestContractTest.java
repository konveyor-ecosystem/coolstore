package com.redhat.coolstore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** REST contract + order flow tests on H2 (Flyway seed data) with in-memory messaging. */
@QuarkusTest
class RestContractTest {

    @Test
    void productsList() {
        given().get("/services/products").then().statusCode(200).contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(9))
                .body("find { it.itemId == '329299' }.name", equalTo("Quarkus T-shirt"))
                .body("[0]", hasKey("desc")).body("[0]", hasKey("link")).body("[0]", hasKey("location"))
                .body("[0]", hasKey("quantity")).body("[0]", hasKey("price"));
    }

    @Test
    void productById() {
        given().get("/services/products/329299").then().statusCode(200)
                .body("itemId", equalTo("329299")).body("price", equalTo(10.0f))
                .body("quantity", greaterThan(0));
    }

    @Test
    void cartsAreIsolatedAndPriced() {
        given().post("/services/cart/cartA/329299/2").then().statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(1))
                .body("cartItemTotal", equalTo(15.0f))            // 2 x 10.00 with 25% off
                .body("cartItemPromoSavings", equalTo(-5.0f))
                .body("shippingTotal", equalTo(2.99f))
                .body("cartTotal", equalTo(17.99f))
                .body("shoppingCartItemList[0]", hasKey("promoSavings"))
                .body("shoppingCartItemList[0].product.itemId", equalTo("329299"));
        given().get("/services/cart/cartB").then().statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(0)).body("cartTotal", equalTo(0.0f));
    }

    @Test
    void addMergeDelete() {
        given().post("/services/cart/tmp1/165613/2").then().statusCode(200);
        given().post("/services/cart/tmp1/165613/1").then().statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(1))
                .body("shoppingCartItemList[0].quantity", equalTo(3));
        given().post("/services/cart/user1/tmp1").then().statusCode(200)
                .body("shoppingCartItemList[0].quantity", equalTo(3));
        given().delete("/services/cart/user1/165613/1").then().statusCode(200)
                .body("shoppingCartItemList[0].quantity", equalTo(2));
        given().delete("/services/cart/user1/165613/5").then().statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(0));
    }

    @Test
    void checkoutPersistsOrderAndDecrementsInventory() {
        int before = given().get("/services/products/444434").then().extract().path("quantity");
        given().post("/services/cart/buyer/444434/3").then().statusCode(200);
        given().post("/services/cart/checkout/buyer").then().statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(0));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given().get("/services/products/444434").then().body("quantity", equalTo(before - 3)));
        int orderId = given().get("/services/orders").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("find { it.itemList[0].productId == '444434' }.itemList[0].quantity", equalTo(3))
                .extract().path("find { it.itemList[0].productId == '444434' }.orderId");
        given().get("/services/orders/" + orderId).then().statusCode(200)
                .body("orderId", equalTo(orderId)).body("shippingFee", notNullValue())
                .body("customerName", notNullValue());
    }

    @Test
    void staticUiAndHealth() {
        given().get("/").then().statusCode(200).body(containsString("ng-view"));
        given().get("/app/app.js").then().statusCode(200);
        given().get("/coolstore.json").then().statusCode(200);
        given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
    }
}
