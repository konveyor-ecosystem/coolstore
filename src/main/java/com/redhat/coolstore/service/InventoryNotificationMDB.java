package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;

import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.util.List;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    @Channel("orders")
    Emitter<String> orderEmitter;

    @Incoming("inventory")
    public void onMessage(String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        List<OrderItem> orderItems = order.getItemList();
        for (OrderItem orderItem : orderItems) {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                // Emit message to orders channel
                orderEmitter.send(orderStr);
            } else {
                orderItem.setQuantity(new_quantity);
            }
        }
    }

    // Other methods remain the same as in Java EE code
}