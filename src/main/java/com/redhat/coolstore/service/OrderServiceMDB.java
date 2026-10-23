package com.redhat.coolstore.service;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.reactive.messaging.annotations.Blocking;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/** Order consumer (was a JMS @MessageDriven bean on topic/orders). */
@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    Logger log;

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    @Blocking
    @Transactional
    public void onMessage(String orderStr) {
        log.info("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        orderService.save(order);
        order.getItemList().forEach(orderItem ->
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity()));
    }
}
