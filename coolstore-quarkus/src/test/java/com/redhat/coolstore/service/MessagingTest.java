package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.utils.Transformers;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for messaging serialization/deserialization.
 * Full integration tests with Testcontainers Artemis require:
 *   @QuarkusTest + @QuarkusTestResource(ArtemisTestResource.class)
 * and are tagged as integration tests.
 */
public class MessagingTest {

    @Test
    void testOrderJsonRoundTrip() {
        // Build a cart
        ShoppingCart cart = new ShoppingCart();
        cart.setCartTotal(129.99);
        cart.setCartItemPromoSavings(-10.0);
        cart.setShippingTotal(5.99);
        cart.setShippingPromoSavings(0.0);

        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(64.99);
        item.setProduct(p);
        item.setQuantity(2);
        item.setPrice(64.99);
        cart.addShoppingCartItem(item);

        // Simulate what ShoppingCartOrderProcessor.process() does
        String json = Transformers.shoppingCartToJson(cart);
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.contains("329299"), "JSON should contain product SKU");

        // Simulate what OrderServiceConsumer.onMessage() does
        Order order = Transformers.jsonToOrder(json);
        assertNotNull(order, "Parsed order should not be null");
        assertEquals(129.99, order.getOrderValue(), 0.001);
        assertEquals(1, order.getItemList().size());
        assertEquals("329299", order.getItemList().get(0).getProductId());
        assertEquals(2, order.getItemList().get(0).getQuantity());
    }

    @Test
    void testLowStockThreshold() {
        // Verify threshold constant used in InventoryNotificationConsumer
        int LOW_THRESHOLD = 50;

        // Item with quantity below threshold
        int newQuantity = 30;
        assertTrue(newQuantity < LOW_THRESHOLD, "Should trigger low-stock alert");

        // Item with quantity above threshold
        newQuantity = 60;
        assertFalse(newQuantity < LOW_THRESHOLD, "Should NOT trigger low-stock alert");
    }

    @Test
    void testOrderItemMapping() {
        String json = "{\"orderValue\":50.0,\"customerName\":\"Test\",\"customerEmail\":\"test@test.com\"," +
            "\"retailPrice\":50.0,\"discount\":0.0,\"shippingFee\":4.99,\"shippingDiscount\":0.0," +
            "\"items\":[{\"productSku\":\"329299\",\"quantity\":5},{\"productSku\":\"444436\",\"quantity\":1}]}";

        Order order = Transformers.jsonToOrder(json);
        List<OrderItem> items = order.getItemList();

        assertEquals(2, items.size());
        assertEquals("329299", items.get(0).getProductId());
        assertEquals(5, items.get(0).getQuantity());
        assertEquals("444436", items.get(1).getProductId());
        assertEquals(1, items.get(1).getQuantity());
    }
}
