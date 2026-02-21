package com.redhat.coolstore.service;

import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import jakarta.inject.Inject;
import jakarta.jms.Message; // Updated import statement

import javax.enterprise.context.ApplicationScoped; // Added import for CDI ApplicationScoped annotation

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders") // Updated annotation to use @Incoming
    public void onMessage(String orderStr) {
        System.out.println("\nMessage recd !");
        System.out.println("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        System.out.println("Order object is " + order);
        orderService.save(order);
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
        });
    }

}