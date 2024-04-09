package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import jakarta.inject.Inject;
import jakarta.naming.InitialContext;
import jakarta.naming.NamingException;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

public class InventoryNotificationMDB implements MessageListener {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    @Channel("topic/orders")
    Emitter<String> orderEmitter;

    @Override
    public void onMessage(Message<String> rcvMessage) {
        String orderStr = rcvMessage.getPayload();
        Order order = Transformers.jsonToOrder(orderStr);
        order.getItemList().forEach(orderItem -> {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
            } else {
                orderItem.setQuantity(new_quantity);
            }
        });

        // Emit the updated order to the "topic/orders" channel
        orderEmitter.send(Message.of(Transformers.orderToJson(order)));
    }

    // ... init() and close() methods remain unchanged
}
