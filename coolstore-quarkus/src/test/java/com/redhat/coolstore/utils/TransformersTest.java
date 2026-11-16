package com.redhat.coolstore.utils;

import com.redhat.coolstore.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransformersTest {

    @Test
    void testToProduct() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("329299");
        entity.setName("Quarkus Shirt");
        entity.setDesc("A great shirt");
        entity.setPrice(29.99);

        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("329299");
        inv.setLocation("Raleigh");
        inv.setQuantity(100);
        inv.setLink("http://example.com/329299");
        entity.setInventory(inv);

        Product prod = Transformers.toProduct(entity);

        assertEquals("329299", prod.getItemId());
        assertEquals("Quarkus Shirt", prod.getName());
        assertEquals("A great shirt", prod.getDesc());
        assertEquals(29.99, prod.getPrice(), 0.001);
        assertEquals("Raleigh", prod.getLocation());
        assertEquals(100, prod.getQuantity());
        assertEquals("http://example.com/329299", prod.getLink());
    }

    @Test
    void testToProductNoInventory() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("329299");
        entity.setName("Quarkus Shirt");
        entity.setPrice(29.99);
        entity.setInventory(null);

        Product prod = Transformers.toProduct(entity);

        assertEquals("329299", prod.getItemId());
        // Should not throw, just leave location/quantity/link as default
        assertNull(prod.getLocation());
        assertEquals(0, prod.getQuantity());
    }

    @Test
    void testShoppingCartToJsonAndBack() {
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

        String json = Transformers.shoppingCartToJson(cart);

        assertNotNull(json);
        assertTrue(json.contains("orderValue"));
        assertTrue(json.contains("329299"));
        assertTrue(json.contains("items"));

        // Round-trip via jsonToOrder
        Order order = Transformers.jsonToOrder(json);
        assertNotNull(order);
        assertEquals(129.99, order.getOrderValue(), 0.001);
        assertEquals(1, order.getItemList().size());
        assertEquals("329299", order.getItemList().get(0).getProductId());
        assertEquals(2, order.getItemList().get(0).getQuantity());
    }

    @Test
    void testJsonToOrder() {
        String json = "{\"orderValue\":129.99,\"customerName\":\"Test User\",\"customerEmail\":\"test@example.com\"," +
                "\"retailPrice\":129.99,\"discount\":-10.0,\"shippingFee\":5.99,\"shippingDiscount\":0.0," +
                "\"items\":[{\"productSku\":\"329299\",\"quantity\":2}]}";

        Order order = Transformers.jsonToOrder(json);

        assertEquals("Test User", order.getCustomerName());
        assertEquals("test@example.com", order.getCustomerEmail());
        assertEquals(129.99, order.getOrderValue(), 0.001);
        assertEquals(1, order.getItemList().size());
        assertEquals("329299", order.getItemList().get(0).getProductId());
    }
}
