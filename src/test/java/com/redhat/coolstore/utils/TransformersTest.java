package com.redhat.coolstore.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.InventoryEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the cart/order JSON transformers consumed by the JMS
 * order flow (plain JUnit, no Quarkus). The JSON produced by
 * {@link Transformers#shoppingCartToJson} is the wire contract between the
 * cart service and the order topic consumer, so the top-level key order is
 * asserted exactly.
 */
class TransformersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Must stay in sync with the RANDOM arrays in Transformers (they pair by index)
    private static final List<String> RANDOM_NAMES = Arrays.asList(
            "Sven Karlsson", "Johan Andersson", "Karl Svensson",
            "Anders Johansson", "Stefan Olson", "Martin Ericsson");
    private static final List<String> RANDOM_EMAILS = Arrays.asList(
            "sven@gmail.com", "johan@gmail.com", "karl@gmail.com",
            "anders@gmail.com", "stefan@gmail.com", "martin@gmail.com");

    private Product product(String itemId, double price) {
        Product p = new Product();
        p.setItemId(itemId);
        p.setName("Product " + itemId);
        p.setPrice(price);
        return p;
    }

    private ShoppingCartItem item(Product p, int quantity, double price) {
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(quantity);
        sci.setPrice(price);
        return sci;
    }

    @Test
    @DisplayName("shoppingCartToJson: exact top-level key order and values, known random customer")
    void shoppingCartToJson_contract() throws Exception {
        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(item(product("329299", 50.0), 2, 50.0));
        cart.setCartTotal(100.0);
        cart.setCartItemPromoSavings(-25.0);
        cart.setShippingTotal(7.99);
        cart.setShippingPromoSavings(0.0);

        JsonNode root = MAPPER.readTree(Transformers.shoppingCartToJson(cart));

        // top-level keys present in EXACT order (wire contract for the orders topic)
        List<String> fieldNames = new ArrayList<>();
        for (Iterator<String> it = root.fieldNames(); it.hasNext();) {
            fieldNames.add(it.next());
        }
        assertEquals(Arrays.asList(
                "orderValue", "customerName", "customerEmail",
                "retailPrice", "discount", "shippingFee", "shippingDiscount", "items"),
                fieldNames);

        assertEquals(100.0, root.get("orderValue").asDouble(), 1e-9);
        assertEquals(100.0, root.get("retailPrice").asDouble(), 1e-9); // 2 * 50.0
        assertEquals(-25.0, root.get("discount").asDouble(), 1e-9);
        assertEquals(7.99, root.get("shippingFee").asDouble(), 1e-9);
        assertEquals(0.0, root.get("shippingDiscount").asDouble(), 1e-9);

        JsonNode items = root.get("items");
        assertTrue(items.isArray(), "items must be a JSON array");
        assertEquals(1, items.size());
        assertEquals("329299", items.get(0).get("productSku").asText());
        assertEquals(2, items.get(0).get("quantity").asInt());

        String customerName = root.get("customerName").asText();
        String customerEmail = root.get("customerEmail").asText();
        int nameIndex = RANDOM_NAMES.indexOf(customerName);
        int emailIndex = RANDOM_EMAILS.indexOf(customerEmail);
        assertTrue(nameIndex >= 0, "unknown customerName: " + customerName);
        assertTrue(emailIndex >= 0, "unknown customerEmail: " + customerEmail);
        assertEquals(nameIndex, emailIndex, "customerName/customerEmail must pair by index");
    }

    @Test
    @DisplayName("jsonToOrder: canonical order JSON maps to a matching Order with items")
    void jsonToOrder_canonicalJson() {
        String json = """
                {
                  "orderValue": 100.0,
                  "customerName": "Karl Svensson",
                  "customerEmail": "karl@gmail.com",
                  "retailPrice": 100.0,
                  "discount": -25.0,
                  "shippingFee": 7.99,
                  "shippingDiscount": 0.0,
                  "items": [
                    { "productSku": "329299", "quantity": 2 }
                  ]
                }
                """;

        Order order = Transformers.jsonToOrder(json);

        assertEquals("Karl Svensson", order.getCustomerName());
        assertEquals("karl@gmail.com", order.getCustomerEmail());
        assertEquals(100.0, order.getOrderValue(), 1e-9);
        assertEquals(100.0, order.getRetailPrice(), 1e-9);
        assertEquals(-25.0, order.getDiscount(), 1e-9);
        assertEquals(7.99, order.getShippingFee(), 1e-9);
        assertEquals(0.0, order.getShippingDiscount(), 1e-9);

        assertEquals(1, order.getItemList().size());
        OrderItem oi = order.getItemList().get(0);
        assertEquals("329299", oi.getProductId());
        assertEquals(2, oi.getQuantity());
    }

    @Test
    @DisplayName("round trip shoppingCartToJson -> jsonToOrder preserves the item list")
    void roundTrip_preservesItemList() {
        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(item(product("329299", 50.0), 2, 50.0));
        cart.addShoppingCartItem(item(product("165614", 14.45), 1, 14.45));
        cart.setCartItemTotal(114.45);
        cart.setCartItemPromoSavings(-25.0);
        cart.setShippingTotal(0.0);
        cart.setShippingPromoSavings(0.0);
        cart.setCartTotal(114.45);

        Order order = Transformers.jsonToOrder(Transformers.shoppingCartToJson(cart));

        assertEquals(2, order.getItemList().size());
        assertEquals(114.45, order.getOrderValue(), 1e-9);
        assertEquals(2 * 50.0 + 1 * 14.45, order.getRetailPrice(), 1e-9);
        assertEquals(-25.0, order.getDiscount(), 1e-9);

        List<String> skus = order.getItemList().stream().map(OrderItem::getProductId).toList();
        assertTrue(skkusContainsAll(skus, "329299", "165614"));
        for (OrderItem oi : order.getItemList()) {
            if ("329299".equals(oi.getProductId())) {
                assertEquals(2, oi.getQuantity());
            } else if ("165614".equals(oi.getProductId())) {
                assertEquals(1, oi.getQuantity());
            }
        }
    }

    private static boolean skkusContainsAll(List<String> skus, String... expected) {
        return skus.containsAll(Arrays.asList(expected));
    }

    @Test
    @DisplayName("toProduct: all fields mapped when inventory is present")
    void toProduct_mapsAllFields_whenInventoryPresent() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("329299");
        entity.setName("Quarkus T-shirt");
        entity.setDesc("A t-shirt");
        entity.setPrice(10.0);
        InventoryEntity inventory = new InventoryEntity();
        inventory.setItemId("329299");
        inventory.setLocation("Raleigh");
        inventory.setLink("http://maps.google.com/?q=Raleigh");
        inventory.setQuantity(736);
        entity.setInventory(inventory);

        Product product = Transformers.toProduct(entity);

        assertEquals("329299", product.getItemId());
        assertEquals("Quarkus T-shirt", product.getName());
        assertEquals("A t-shirt", product.getDesc());
        assertEquals(10.0, product.getPrice(), 1e-9);
        assertEquals("Raleigh", product.getLocation());
        assertEquals("http://maps.google.com/?q=Raleigh", product.getLink());
        assertEquals(736, product.getQuantity());
    }

    @Test
    @DisplayName("toProduct: inventory == null -> no exception, quantity 0 (warning path)")
    void toProduct_missingInventory_noException_quantityZero() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("165614");
        entity.setName("Quarkus H2Go water bottle");
        entity.setDesc("A bottle");
        entity.setPrice(14.45);
        entity.setInventory(null);

        Product product = assertDoesNotThrow(() -> Transformers.toProduct(entity));

        assertEquals("165614", product.getItemId());
        assertEquals("Quarkus H2Go water bottle", product.getName());
        assertEquals(14.45, product.getPrice(), 1e-9);
        assertEquals(0, product.getQuantity());
        assertNull(product.getLocation());
        assertNull(product.getLink());
    }
}
