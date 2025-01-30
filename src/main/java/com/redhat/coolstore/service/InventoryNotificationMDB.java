package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Incoming;
import jakarta.inject.Inject;

import javax.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

@ApplicationScoped
public class InventoryNotificationService {

    private static final int LOW_THRESHOLD = 50;
    private static final Logger LOGGER = Logger.getLogger(InventoryNotificationService.class.getName());

    @Inject
    private CatalogService catalogService;

    @Incoming("orders")
    public void processOrder(String orderStr) {
        try {
            LOGGER.info("received message inventory");
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                if (new_quantity < LOW_THRESHOLD) {
                    LOGGER.info("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                } else {
                    orderItem.setQuantity(new_quantity);
                }
            });
        } catch (Exception e) {
            LOGGER.severe("An exception occurred: " + e.getMessage());
        }
    }
}