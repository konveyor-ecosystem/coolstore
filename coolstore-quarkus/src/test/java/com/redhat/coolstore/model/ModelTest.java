package com.redhat.coolstore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {

    @Test
    void testShoppingCartAddItem() {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("329299");
        p.setName("Test Product");
        p.setPrice(100.0);
        item.setProduct(p);
        item.setQuantity(2);
        item.setPrice(100.0);

        cart.addShoppingCartItem(item);

        assertEquals(1, cart.getShoppingCartItemList().size());
        assertEquals("329299", cart.getShoppingCartItemList().get(0).getProduct().getItemId());
    }

    @Test
    void testShoppingCartRemoveItem() {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("329299");
        item.setProduct(p);
        cart.addShoppingCartItem(item);

        boolean removed = cart.removeShoppingCartItem(item);

        assertTrue(removed);
        assertEquals(0, cart.getShoppingCartItemList().size());
    }

    @Test
    void testShoppingCartReset() {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem item = new ShoppingCartItem();
        cart.addShoppingCartItem(item);

        cart.resetShoppingCartItemList();

        assertEquals(0, cart.getShoppingCartItemList().size());
    }

    @Test
    void testShoppingCartItemToOrderItem() {
        ShoppingCartItem sci = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("329299");
        sci.setProduct(p);
        sci.setQuantity(3);

        OrderItem oi = sci.toOrderItem();

        assertEquals("329299", oi.getProductId());
        assertEquals(3, oi.getQuantity());
    }

    @Test
    void testPromotionConstructor() {
        Promotion promo = new Promotion("329299", 0.25);
        assertEquals("329299", promo.getItemId());
        assertEquals(0.25, promo.getPercentOff(), 0.001);
    }
}
