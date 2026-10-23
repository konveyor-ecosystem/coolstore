package com.redhat.coolstore.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.InventoryEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

class TransformersTest {

    @Test
    void cartJsonRoundTripsToOrder() {
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(10.0);
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setPrice(7.5);
        sci.setQuantity(2);
        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(sci);
        cart.setCartTotal(17.99);
        cart.setCartItemPromoSavings(-5.0);
        cart.setShippingTotal(2.99);
        cart.setShippingPromoSavings(0);

        Order o = Transformers.jsonToOrder(Transformers.shoppingCartToJson(cart));
        assertEquals(17.99, o.getOrderValue(), 1e-9);
        assertEquals(15.0, o.getRetailPrice(), 1e-9);
        assertEquals(-5.0, o.getDiscount(), 1e-9);
        assertEquals(2.99, o.getShippingFee(), 1e-9);
        assertEquals(0, o.getShippingDiscount(), 1e-9);
        assertNotNull(o.getCustomerName());
        assertTrue(o.getCustomerEmail().contains("@"));
        assertEquals(1, o.getItemList().size());
        assertEquals("329299", o.getItemList().get(0).getProductId());
        assertEquals(2, o.getItemList().get(0).getQuantity());
    }

    @Test
    void emptyCartProducesOrderWithoutItems() {
        Order o = Transformers.jsonToOrder(Transformers.shoppingCartToJson(new ShoppingCart()));
        assertTrue(o.getItemList().isEmpty());
        assertEquals(0, o.getRetailPrice(), 1e-9);
    }

    @Test
    void missingFieldFails() {
        assertThrows(RuntimeException.class, () -> Transformers.jsonToOrder("{\"customerEmail\":\"x\"}"));
    }

    @Test
    void entityToProduct() {
        CatalogItemEntity e = new CatalogItemEntity();
        e.setItemId("1");
        e.setName("n");
        e.setDesc("d");
        e.setPrice(1.5);
        InventoryEntity inv = new InventoryEntity();
        inv.setQuantity(7);
        inv.setLocation("Raleigh");
        inv.setLink("l");
        e.setInventory(inv);
        Product p = Transformers.toProduct(e);
        assertEquals("1", p.getItemId());
        assertEquals("n", p.getName());
        assertEquals(1.5, p.getPrice(), 1e-9);
        assertEquals(7, p.getQuantity());
        assertEquals("Raleigh", p.getLocation());
    }
}
