package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
//import org.eclipse.microprofile.reactive.messaging.TextMessage;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;


import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class OrderServiceMDB {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("topic/orders")
    @Blocking
	@Transactional
    public void onMessage(String orderStr) {
        System.out.println("\nMessage recd !");
        try {
            System.out.println("Received order: " + orderStr);
            Order order = Transformers.jsonToOrder(orderStr);
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
