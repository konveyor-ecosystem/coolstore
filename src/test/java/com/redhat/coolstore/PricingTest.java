package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.PromoService;
import com.redhat.coolstore.service.ShippingService;

/** Characterization tests for shipping tiers, insurance and promotions (plain JUnit 5). */
class PricingTest {

    private final ShippingService shipping = new ShippingService();
    private final PromoService promo = new PromoService();

    private static ShoppingCart cartWithTotal(double total) {
        ShoppingCart c = new ShoppingCart();
        c.setCartItemTotal(total);
        return c;
    }

    @Test
    void shippingTiers() {
        assertEquals(2.99, shipping.calculateShipping(cartWithTotal(10)));
        assertEquals(4.99, shipping.calculateShipping(cartWithTotal(25)));
        assertEquals(6.99, shipping.calculateShipping(cartWithTotal(50)));
        assertEquals(8.99, shipping.calculateShipping(cartWithTotal(75)));
        assertEquals(10.99, shipping.calculateShipping(cartWithTotal(100)));
        assertEquals(0, shipping.calculateShipping(cartWithTotal(10000)));
        assertEquals(0, shipping.calculateShipping(null));
    }

    @Test
    void insurance() {
        assertEquals(0, shipping.calculateShippingInsurance(cartWithTotal(24)));
        assertEquals(0.6, shipping.calculateShippingInsurance(cartWithTotal(30)));
        assertEquals(1.5, shipping.calculateShippingInsurance(cartWithTotal(100)));
        assertEquals(5.0, shipping.calculateShippingInsurance(cartWithTotal(500)));
        assertEquals(0.67, shipping.calculateShippingInsurance(cartWithTotal(33.33))); // HALF_UP
    }

    @Test
    void promo25PercentOn329299() {
        ShoppingCart c = new ShoppingCart();
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(10.0);
        ShoppingCartItem i = new ShoppingCartItem();
        i.setProduct(p);
        i.setPrice(10.0);
        i.setQuantity(1);
        c.addShoppingCartItem(i);
        promo.applyCartItemPromotions(c);
        assertEquals(7.5, i.getPrice());
        assertEquals(-2.5, i.getPromoSavings());
    }

    @Test
    void freeShippingAt75() {
        ShoppingCart c = cartWithTotal(75);
        c.setShippingTotal(8.99);
        promo.applyShippingPromotions(c);
        assertEquals(0, c.getShippingTotal());
        assertEquals(-8.99, c.getShippingPromoSavings());
        ShoppingCart c2 = cartWithTotal(74.99);
        c2.setShippingTotal(6.99);
        promo.applyShippingPromotions(c2);
        assertEquals(6.99, c2.getShippingTotal());
    }
}
