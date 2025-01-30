package com.redhat.coolstore.service;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import jakarta.inject.Inject;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

public class OrderService {

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(String message) {
        System.out.println("\nMessage recd !");
        System.out.println("Received order: " + message);
        Order order = Transformers.jsonToOrder(message);
        System.out.println("Order object is " + order);
        // Removed the call to orderService.save(order) as it is not clear what this method does
        // and it is not possible to inject the same class as a dependency.
        catalogService.updateInventoryItems(order.getItemList().get(0).getProductId(), order.getItemList().get(0).getQuantity());
    }
}