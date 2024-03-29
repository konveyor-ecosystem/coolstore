package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    @Channel("topic/orders")
    Emitter<String> orderEmitter;

    @Incoming("inventory-notifications")
    public void onMessage(Message rcvMessage) {
        TextMessage msg;
        try {
            System.out.println("received message inventory");
            if (rcvMessage instanceof TextMessage) {
                msg = (TextMessage) rcvMessage;
                String orderStr = msg.getBody(String.class);
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
                Uni.createFrom().item(order)
                    .subscribe().with(order1 -> orderEmitter.send(Transformers.orderToJson(order1)));
            }
        } catch (JMSException jmse) {
            System.err.println("An exception occurred: " + jmse.getMessage());
        }
    }

    public void init() throws NamingException, JMSException {
        // Not needed in Quarkus
    }

    public void close() throws JMSException {
        // Not needed in Quarkus
    }
}
