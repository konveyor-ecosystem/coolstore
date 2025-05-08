package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.logging.Logger;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    Logger log;

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(String orderStr) {
        log.info("Message received!");
        try {
            log.info("Received order: " + orderStr);
            Order order = Transformers.jsonToOrder(orderStr);
            log.info("Order object is " + order);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}