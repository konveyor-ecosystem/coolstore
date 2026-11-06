package com.redhat.coolstore.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.jboss.logging.Logger;

/**
 * Created by tqvarnst on 2017-03-30.
 */
public class Transformers {

    private static final String[] RANDOM_NAMES = {"Sven Karlsson","Johan Andersson","Karl Svensson","Anders Johansson","Stefan Olson","Martin Ericsson"};
    private static final String[] RANDOM_EMAILS = {"sven@gmail.com","johan@gmail.com","karl@gmail.com","anders@gmail.com","stefan@gmail.com","martin@gmail.com"};

    private static final Logger log = Logger.getLogger(Transformers.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Product toProduct(CatalogItemEntity entity) {
        Product prod = new Product();
        prod.setItemId(entity.getItemId());
        prod.setName(entity.getName());
        prod.setDesc(entity.getDesc());
        prod.setPrice(entity.getPrice());
        if (entity.getInventory() != null) {
            prod.setLocation(entity.getInventory().getLocation());
            prod.setLink(entity.getInventory().getLink());
            prod.setQuantity(entity.getInventory().getQuantity());
        } else {
            log.warn("Inventory for " + entity.getName() + "[" + entity.getItemId() + "] unknown and missing");
        }
        return prod;
    }

    public static String shoppingCartToJson(ShoppingCart cart) {
        List<Map<String, Object>> items = new ArrayList<>();
        cart.getShoppingCartItemList().forEach(item -> {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("productSku", item.getProduct().getItemId());
            itemMap.put("quantity", item.getQuantity());
            items.add(itemMap);
        });

        int randomNameAndEmailIndex = ThreadLocalRandom.current().nextInt(RANDOM_NAMES.length);

        // LinkedHashMap keeps insertion order so the JSON key contract is preserved exactly:
        // orderValue, customerName, customerEmail, retailPrice, discount, shippingFee, shippingDiscount, items
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("orderValue", Double.valueOf(cart.getCartTotal()));
        root.put("customerName", RANDOM_NAMES[randomNameAndEmailIndex]);
        root.put("customerEmail", RANDOM_EMAILS[randomNameAndEmailIndex]);
        root.put("retailPrice", Double.valueOf(cart.getShoppingCartItemList().stream().mapToDouble(i -> i.getQuantity() * i.getPrice()).sum()));
        root.put("discount", Double.valueOf(cart.getCartItemPromoSavings()));
        root.put("shippingFee", Double.valueOf(cart.getShippingTotal()));
        root.put("shippingDiscount", Double.valueOf(cart.getShippingPromoSavings()));
        root.put("items", items);

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize shopping cart to JSON", e);
        }
    }

    public static Order jsonToOrder(String json) {
        final JsonNode rootObject;
        try {
            rootObject = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse order JSON", e);
        }
        Order order = new Order();
        order.setCustomerName(rootObject.get("customerName").asText());
        order.setCustomerEmail(rootObject.get("customerEmail").asText());
        order.setOrderValue(rootObject.get("orderValue").asDouble());
        order.setRetailPrice(rootObject.get("retailPrice").asDouble());
        order.setDiscount(rootObject.get("discount").asDouble());
        order.setShippingFee(rootObject.get("shippingFee").asDouble());
        order.setShippingDiscount(rootObject.get("shippingDiscount").asDouble());
        JsonNode jsonItems = rootObject.get("items");
        List<OrderItem> items = new ArrayList<OrderItem>(jsonItems.size());
        for (JsonNode jsonItem : jsonItems) {
            OrderItem oi = new OrderItem();
            oi.setProductId(jsonItem.get("productSku").asText());
            oi.setQuantity(jsonItem.get("quantity").asInt());
            items.add(oi);
        }
        order.setItemList(items);
        return order;
    }


}
