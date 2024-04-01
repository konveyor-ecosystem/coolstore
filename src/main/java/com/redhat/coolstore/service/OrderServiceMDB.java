package com.redhat.coolstore.service;

import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("topic/orders")
    public void onMessage(Message<String> rcvMessage) {
        System.out.println("\nMessage recd !");
        TextMessage msg = null;
        try {
            if (rcvMessage.getPayload() != null) {
                String orderStr = rcvMessage.getPayload();
                System.out.println("Received order: " + orderStr);
                Order order = Transformers.jsonToOrder(orderStr);
                System.out.println("Order object is " + order);
                orderService.save(order);
                order.getItemList().forEach(orderItem -> {
                    catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                });
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
