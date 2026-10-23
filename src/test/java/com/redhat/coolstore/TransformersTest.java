package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.utils.Transformers;

class TransformersTest {

    @Test
    void cartToJsonToOrderRoundTrip() {
        ShoppingCart c = new ShoppingCart();
        Product p = new Product();
        p.setItemId("165613");
        ShoppingCartItem i = new ShoppingCartItem();
        i.setProduct(p);
        i.setPrice(4.15);
        i.setQuantity(2);
        c.addShoppingCartItem(i);
        c.setCartTotal(11.29);
        c.setShippingTotal(2.99);
        String json = Transformers.shoppingCartToJson(c);
        for (String k : new String[] {"productSku", "quantity", "orderValue", "customerName", "customerEmail",
                "retailPrice", "discount", "shippingFee", "shippingDiscount", "items"}) {
            assertTrue(json.contains("\"" + k + "\""), k);
        }
        Order o = Transformers.jsonToOrder(json);
        assertEquals(11.29, o.getOrderValue());
        assertEquals(8.3, o.getRetailPrice(), 0.0001);
        assertEquals(2.99, o.getShippingFee());
        assertEquals(1, o.getItemList().size());
        assertEquals("165613", o.getItemList().get(0).getProductId());
        assertEquals(2, o.getItemList().get(0).getQuantity());
    }
}
