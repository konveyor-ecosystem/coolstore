package com.redhat.coolstore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.ShoppingCart;

/** Characterization of the legacy shipping tiers and insurance rates. */
class ShippingServiceTest {
    private final ShippingService svc = new ShippingService();

    private static ShoppingCart cart(double total) {
        ShoppingCart c = new ShoppingCart();
        c.setCartItemTotal(total);
        return c;
    }

    @ParameterizedTest
    @CsvSource({"0,2.99", "24.99,2.99", "25,4.99", "49.99,4.99", "50,6.99", "74.99,6.99", "75,8.99",
            "99.99,8.99", "100,10.99", "9999.99,10.99", "10000,0", "-1,0"})
    void shippingTiers(double total, double expected) {
        assertEquals(expected, svc.calculateShipping(cart(total)), 0.0001);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "24.99,0", "25,0.5", "99.99,2.0", "100,1.5", "499.99,7.5", "500,5.0", "9999,99.99", "10000,0"})
    void insuranceTiers(double total, double expected) {
        assertEquals(expected, svc.calculateShippingInsurance(cart(total)), 0.0001);
    }

    @Test
    void nullCart() {
        assertEquals(0, svc.calculateShipping(null));
        assertEquals(0, svc.calculateShippingInsurance(null));
    }
}
