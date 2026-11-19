package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.service.ShippingService;

/** Characterization of the EAP shipping tiers (2.99-10.99) and insurance (2%/1.5%/1%). */
class ShippingServiceTest {

    private final ShippingService svc = new ShippingService();
    private ShoppingCart cart(double total) {
        ShoppingCart sc = new ShoppingCart();
        sc.setCartItemTotal(total);
        return sc;
    }

    @Test
    void shippingTiers() {
        assertEquals(2.99, svc.calculateShipping(cart(0)));
        assertEquals(2.99, svc.calculateShipping(cart(24.99)));
        assertEquals(4.99, svc.calculateShipping(cart(25)));
        assertEquals(4.99, svc.calculateShipping(cart(49.99)));
        assertEquals(6.99, svc.calculateShipping(cart(50)));
        assertEquals(6.99, svc.calculateShipping(cart(74.99)));
        assertEquals(8.99, svc.calculateShipping(cart(75)));
        assertEquals(8.99, svc.calculateShipping(cart(99.99)));
        assertEquals(10.99, svc.calculateShipping(cart(100)));
        assertEquals(10.99, svc.calculateShipping(cart(9999.99)));
        assertEquals(0, svc.calculateShipping(cart(10000)));
        assertEquals(0, svc.calculateShipping(null));
    }

    @Test
    void insuranceTiers() {
        assertEquals(0, svc.calculateShippingInsurance(cart(24.99)));
        assertEquals(0.50, svc.calculateShippingInsurance(cart(25)));   // 2%
        assertEquals(1.99, svc.calculateShippingInsurance(cart(99.5))); // 2% of 99.5 = 1.99
        assertEquals(1.50, svc.calculateShippingInsurance(cart(100)));  // 1.5%
        assertEquals(7.50, svc.calculateShippingInsurance(cart(499.99))); // 1.5%
        assertEquals(5.00, svc.calculateShippingInsurance(cart(500)));  // 1%
        assertEquals(10.00, svc.calculateShippingInsurance(cart(1000))); // 1%
        assertEquals(0, svc.calculateShippingInsurance(cart(10000)));
        assertEquals(0, svc.calculateShippingInsurance(null));
    }
}
