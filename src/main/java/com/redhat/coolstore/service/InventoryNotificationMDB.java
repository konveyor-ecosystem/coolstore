package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.logging.Logger;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Inject
    Logger log;

    @Incoming("orders")
    public void onMessage(String orderStr) {
        try {
            log.info("Received inventory message");
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                if (new_quantity < LOW_THRESHOLD) {
                    log.warning("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                }
            });
        } catch (Exception e) {
            log.severe("Error processing inventory message: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}