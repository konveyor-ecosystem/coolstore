package com.redhat.coolstore.service;

import io.smallrye.reactive.messaging.annotations.Incoming;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Inject
    Jsonb jsonb;

    @Incoming("orders")
    public void onMessage(String message) {
        System.out.println("\nMessage recd !");
        try {
            Order order = jsonb.fromJson(message, Order.class);
            System.out.println("Received order: " + order);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}