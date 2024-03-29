package com.redhat.coolstore.service;

import jakarta.inject.Inject;
import jakarta.jms.TextMessage;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("topic/orders")
    public void onMessage(TextMessage rcvMessage) {
        System.out.println("\nMessage recd !");
        TextMessage msg = null;
        try {
                if (rcvMessage instanceof TextMessage) {
                    msg = rcvMessage;
                    String orderStr = msg.getBody(String.class);
                    System.out.println("Received order: " + orderStr);
                    Order order = Transformers.jsonToOrder(orderStr);
                    System.out.println("Order object is " + order);
                    orderService.save(order);
                    order.getItemList().forEach(orderItem -> {
                        catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                    });
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }
}
