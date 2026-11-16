package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShippingServiceTest {

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService();
    }

    @Test
    void testCalculateShipping_under25() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(20.0);
        assertEquals(2.99, shippingService.calculateShipping(cart), 0.001);
    }

    @Test
    void testCalculateShipping_25to50() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(30.0);
        assertEquals(4.99, shippingService.calculateShipping(cart), 0.001);
    }

    @Test
    void testCalculateShipping_50to75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(60.0);
        assertEquals(6.99, shippingService.calculateShipping(cart), 0.001);
    }

    @Test
    void testCalculateShipping_75to100() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(80.0);
        assertEquals(8.99, shippingService.calculateShipping(cart), 0.001);
    }

    @Test
    void testCalculateShipping_over100() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(150.0);
        assertEquals(10.99, shippingService.calculateShipping(cart), 0.001);
    }

    @Test
    void testCalculateShipping_nullCart() {
        assertEquals(0.0, shippingService.calculateShipping(null), 0.001);
    }

    @Test
    void testCalculateShippingInsurance_25to100() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(50.0);
        // 2% of 50 = 1.00
        assertEquals(1.00, shippingService.calculateShippingInsurance(cart), 0.001);
    }

    @Test
    void testCalculateShippingInsurance_100to500() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(200.0);
        // 1.5% of 200 = 3.00
        assertEquals(3.00, shippingService.calculateShippingInsurance(cart), 0.001);
    }

    @Test
    void testCalculateShippingInsurance_under25() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(10.0);
        assertEquals(0.0, shippingService.calculateShippingInsurance(cart), 0.001);
    }
}
