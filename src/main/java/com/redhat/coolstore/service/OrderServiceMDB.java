package com.redhat.coolstore.service;

import io.smallrye.reactive.messaging.Message;
import io.smallrye.reactive.messaging.MessageListener;
import io.smallrye.reactive.messaging.Incoming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(Message<String> rcvMessage) {
        System.out.println("\nMessage recd !");
        String orderStr = rcvMessage.getPayload();
        System.out.println("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        System.out.println("Order object is " + order);
        orderService.save(order);
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
        });
    }

}