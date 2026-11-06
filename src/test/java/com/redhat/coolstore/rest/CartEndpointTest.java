package com.redhat.coolstore.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.service.ShoppingCartOrderProcessor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Contract tests for /services/cart under the %test profile (H2 catalog,
 * auth permitted, JMS consumers disabled).
 *
 * <p>The %test environment has no Artemis broker. The only broker dependency
 * in this flow is {@link ShoppingCartOrderProcessor#process}, which publishes
 * the cart JSON to topic/orders; it is mocked with {@code @InjectMock} so the
 * checkout HTTP contract (order handoff + cart reset) stays testable without
 * a live broker, mirroring the profile's "no JMS" philosophy.</p>
 */
@QuarkusTest
class CartEndpointTest {

    @InjectMock
    ShoppingCartOrderProcessor orderProcessor;

    @Test
    @DisplayName("GET /services/cart/testcart -> 200")
    void getCart_returnsCart() {
        given()
                .when().get("/services/cart/testcart")
                .then()
                .statusCode(200)
                .body("cartTotal", notNullValue());
    }

    @Test
    @DisplayName("POST /services/cart/testcart/329299/2 -> 200, cart is priced (cartItemTotal > 0)")
    void addItem_pricedCartReturned() {
        // 329299 is seeded at 10.00 with a 25% promo: 2 * 10.00 * 0.75 = 15.00
        given()
                .when().post("/services/cart/testcart/329299/2")
                .then()
                .statusCode(200)
                .body("cartItemTotal", greaterThan(0f))
                .body("shoppingCartItemList.size()", equalTo(1));
    }

    @Test
    @DisplayName("POST /services/cart/checkout/testcart -> 200, order handed to processor, cart reset")
    void checkout_handOffAndReset() {
        // seed the cart so the checkout has something to hand off and reset
        given()
                .when().post("/services/cart/testcart/329299/2")
                .then()
                .statusCode(200);

        given()
                .when().post("/services/cart/checkout/testcart")
                .then()
                .statusCode(200)
                .body("shoppingCartItemList.size()", equalTo(0));

        verify(orderProcessor).process(any(ShoppingCart.class));
    }
}
