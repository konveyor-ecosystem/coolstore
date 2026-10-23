package com.redhat.coolstore.service;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import io.quarkus.test.junit.QuarkusTest;

/** Pricing characterization against the Flyway seed catalog (329299 = $10.00, 329199 = $9.00). */
@QuarkusTest
class ShoppingCartServiceTest {
    @Inject
    ShoppingCartService svc;

    private static ShoppingCart cartWith(String id, int qty) {
        Product p = new Product();
        p.setItemId(id);
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(qty);
        ShoppingCart c = new ShoppingCart();
        c.addShoppingCartItem(sci);
        return c;
    }

    @Test
    void promotedItemSmallCart() {
        ShoppingCart c = cartWith("329299", 3);
        svc.priceShoppingCart(c);
        assertEquals(22.5, c.getCartItemTotal(), 1e-9);
        assertEquals(-7.5, c.getCartItemPromoSavings(), 1e-9);
        assertEquals(2.99, c.getShippingTotal(), 1e-9);
        assertEquals(0, c.getShippingPromoSavings(), 1e-9);
        assertEquals(25.49, c.getCartTotal(), 1e-9);
    }

    @Test
    void largeCartGetsInsuranceThenFreeShipping() {
        ShoppingCart c = cartWith("329199", 10);
        svc.priceShoppingCart(c);
        assertEquals(90.0, c.getCartItemTotal(), 1e-9);
        assertEquals(0, c.getShippingTotal(), 1e-9);
        assertEquals(-10.79, c.getShippingPromoSavings(), 1e-9); // 8.99 + 2% insurance (1.80)
        assertEquals(90.0, c.getCartTotal(), 1e-9);
    }

    @Test
    void emptyCartIsZero() {
        ShoppingCart c = new ShoppingCart();
        svc.priceShoppingCart(c);
        assertEquals(0, c.getCartTotal(), 1e-9);
        svc.priceShoppingCart(null);
    }

    @Test
    void cartsAreIsolatedPerId() {
        ShoppingCart a = svc.getShoppingCart("iso-a");
        assertSame(a, svc.getShoppingCart("iso-a"));
        assertNotSame(a, svc.getShoppingCart("iso-b"));
        assertNotNull(svc.getProduct("329299"));
    }
}
