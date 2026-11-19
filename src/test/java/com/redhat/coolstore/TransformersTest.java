package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.*;
import com.redhat.coolstore.utils.Transformers;

/** Characterization of the order JSON contract published by Transformers.shoppingCartToJson. */
class TransformersTest {

    @Test
    void shoppingCartToJsonContract() {
        Product p = new Product();
        p.setItemId("329299");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setPrice(7.50);
        sci.setQuantity(2);
        ShoppingCart cart = new ShoppingCart();
        cart.getShoppingCartItemList().add(sci);
        cart.setCartItemTotal(15.0);
        cart.setCartItemPromoSavings(-5.0);
        cart.setShippingTotal(2.99);
        cart.setShippingPromoSavings(0.0);
        cart.setCartTotal(17.99);

        String json = Transformers.shoppingCartToJson(cart);

        assertTrue(json.contains("\"orderValue\""));
        assertTrue(json.contains("\"customerName\""));
        assertTrue(json.contains("\"customerEmail\""));
        assertTrue(json.contains("\"retailPrice\":15.0"));
        assertTrue(json.contains("\"discount\":-5.0"));
        assertTrue(json.contains("\"shippingFee\":2.99"));
        assertTrue(json.contains("\"shippingDiscount\":0.0"));
        assertTrue(json.contains("\"items\":[{\"productSku\":\"329299\",\"quantity\":2}]"));
        // JSON shape must remain compact (no pretty printing) to stay byte-compatible
    }

    @Test
    void jsonToOrderRoundTrip() {
        Product p = new Product();
        p.setItemId("329299");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setPrice(7.50);
        sci.setQuantity(2);
        ShoppingCart cart = new ShoppingCart();
        cart.getShoppingCartItemList().add(sci);
        cart.setCartItemTotal(17.99);
        cart.setCartItemPromoSavings(-5.0);
        cart.setShippingTotal(2.99);
        cart.setShippingPromoSavings(0.0);
        cart.setCartTotal(17.99);

        Order order = Transformers.jsonToOrder(Transformers.shoppingCartToJson(cart));
        assertEquals(17.99, order.getOrderValue());
        assertEquals(-5.0, order.getDiscount());
        assertEquals(2.99, order.getShippingFee());
        assertEquals(1, order.getItemList().size());
        assertEquals("329299", order.getItemList().get(0).getProductId());
        assertEquals(2, order.getItemList().get(0).getQuantity());
    }
}
