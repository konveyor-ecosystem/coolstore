package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PromoServiceTest {

    private PromoService promoService;

    @BeforeEach
    void setUp() {
        promoService = new PromoService();
    }

    @Test
    void testApplyCartItemPromotion_itemId329299() {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(100.0);
        item.setProduct(p);
        item.setQuantity(1);
        item.setPrice(100.0);
        cart.addShoppingCartItem(item);

        promoService.applyCartItemPromotions(cart);

        // 25% off: price should be 75.0
        assertEquals(75.0, item.getPrice(), 0.001, "Price should be reduced by 25%");
        assertEquals(-25.0, item.getPromoSavings(), 0.001, "Promo savings should be -25%");
    }

    @Test
    void testApplyCartItemPromotion_noPromoForOtherItem() {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("other-item");
        p.setPrice(50.0);
        item.setProduct(p);
        item.setQuantity(1);
        item.setPrice(50.0);
        cart.addShoppingCartItem(item);

        promoService.applyCartItemPromotions(cart);

        // No promo: price unchanged
        assertEquals(50.0, item.getPrice(), 0.001);
        assertEquals(0.0, item.getPromoSavings(), 0.001);
    }

    @Test
    void testApplyShippingPromotions_freeShippingOver75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(80.0);
        cart.setShippingTotal(8.99);

        promoService.applyShippingPromotions(cart);

        assertEquals(0.0, cart.getShippingTotal(), 0.001, "Shipping should be free over $75");
        assertEquals(-8.99, cart.getShippingPromoSavings(), 0.001);
    }

    @Test
    void testApplyShippingPromotions_noFreeShippingUnder75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(50.0);
        cart.setShippingTotal(6.99);

        promoService.applyShippingPromotions(cart);

        assertEquals(6.99, cart.getShippingTotal(), 0.001, "Shipping should NOT be free under $75");
        assertEquals(0.0, cart.getShippingPromoSavings(), 0.001);
    }
}
