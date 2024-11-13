
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import io.quarkus.smallrye.reactivemessaging.jms.IncomingJmsMessage;
import io.quarkus.smallrye.reactivemessaging.jms.JmsListener;

import jakarta.inject.Inject; // Updated import statement for Quarkus

import java.util.List;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @JmsListener(destination = "orders")
    public void onMessage(@IncomingJmsMessage String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        List<OrderItem> orderItems = order.getItemList();
        orderItems.forEach(orderItem -> {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
            } else {
                orderItem.setQuantity(new_quantity);
            }
        });
    }

    // Other methods remain unchanged
}
