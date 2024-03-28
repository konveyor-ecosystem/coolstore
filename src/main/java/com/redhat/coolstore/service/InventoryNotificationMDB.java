package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Incoming;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    @Channel("orders-incoming")
    Emitter<Message<String>> orderEmitter;

    @Incoming("orders-incoming")
    @Blocking
    @Transactional
    public void onMessage(String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        List<Order.OrderItem> itemList = order.getItemList();
        for (Order.OrderItem orderItem : itemList) {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
            } else {
                orderItem.setQuantity(new_quantity);
            }
        }
        orderEmitter.send(Message.of(orderStr));
    }
}
