package com.redhat.coolstore.service;

import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;
import io.smallrye.reactive.messaging.Incoming;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Inject
    @Channel("topic/orders")
    Emitter<String> emitter;

    @Incoming("topic/orders")
    public void onMessage(Message<String> message) {
        System.out.println("\nMessage recd !");
        try {
            Order order = Transformers.jsonToOrder(message.getPayload());
            System.out.println("Order object is " + order);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}