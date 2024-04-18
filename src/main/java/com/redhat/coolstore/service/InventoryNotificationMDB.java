package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.annotations.OnMessage;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    @Channel("orders-in")
    Emitter<Order> emitter;

    @OnMessage
    @Blocking
    public void onMessage(Order order) {
        System.out.println("received message inventory");
        order.getItemList().forEach(orderItem -> {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
            } else {
                orderItem.setQuantity(new_quantity);
            }
        });
        emitter.send(order);
    }

    public void init() throws NamingException {
        // Initialization code remains the same
    }

    public void close() {
        // Close code remains the same
    }

    private static InitialContext getInitialContext() throws NamingException {
        // InitialContext code remains the same
    }
}
