package com.redhat.coolstore.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

class PromoServiceTest {
    private final PromoService svc = new PromoService();

    private static ShoppingCartItem item(String id, double price, int qty) {
        Product p = new Product();
        p.setItemId(id);
        p.setPrice(price);
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setPrice(price);
        sci.setQuantity(qty);
        return sci;
    }

    @Test
    void defaultPromotionIs329299At25Percent() {
        assertEquals(1, svc.getPromotions().size());
        var promo = svc.getPromotions().iterator().next();
        assertEquals("329299", promo.getItemId());
        assertEquals(0.25, promo.getPercentOff(), 1e-9);
    }

    @Test
    void itemPromotionAppliedOnlyToPromotedItem() {
        ShoppingCart c = new ShoppingCart();
        c.addShoppingCartItem(item("329299", 10.0, 2));
        c.addShoppingCartItem(item("165613", 4.15, 1));
        svc.applyCartItemPromotions(c);
        ShoppingCartItem promoted = c.getShoppingCartItemList().get(0);
        assertEquals(7.5, promoted.getPrice(), 1e-9);
        assertEquals(-2.5, promoted.getPromoSavings(), 1e-9);
        ShoppingCartItem other = c.getShoppingCartItemList().get(1);
        assertEquals(4.15, other.getPrice(), 1e-9);
        assertEquals(0, other.getPromoSavings(), 1e-9);
    }

    @Test
    void emptyOrNullCartIsIgnored() {
        svc.applyCartItemPromotions(null);
        svc.applyShippingPromotions(null);
        ShoppingCart c = new ShoppingCart();
        svc.applyCartItemPromotions(c);
        assertTrue(c.getShoppingCartItemList().isEmpty());
    }

    @Test
    void freeShippingAtOrAbove75() {
        ShoppingCart c = new ShoppingCart();
        c.setCartItemTotal(75.0);
        c.setShippingTotal(8.99);
        svc.applyShippingPromotions(c);
        assertEquals(0, c.getShippingTotal(), 1e-9);
        assertEquals(-8.99, c.getShippingPromoSavings(), 1e-9);
    }

    @Test
    void noFreeShippingBelow75() {
        ShoppingCart c = new ShoppingCart();
        c.setCartItemTotal(74.99);
        c.setShippingTotal(6.99);
        svc.applyShippingPromotions(c);
        assertEquals(6.99, c.getShippingTotal(), 1e-9);
        assertEquals(0, c.getShippingPromoSavings(), 1e-9);
    }
}
