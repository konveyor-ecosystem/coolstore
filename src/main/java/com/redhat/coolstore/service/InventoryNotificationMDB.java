package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/**
 * Replaces the WebLogic JMS InventoryNotificationMDB (WLInitialContextFactory / t3://localhost:7001).
 * Second consumer on the "orders" AMQP channel; raises a low-inventory alert when the projected
 * quantity falls below the threshold (LOW_THRESHOLD = 50), matching the original behavior.
 */
@ApplicationScoped
public class InventoryNotificationMDB {

    private static final Logger LOG = Logger.getLogger(InventoryNotificationMDB.class);

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Incoming("orders")
    public void onOrder(String orderStr) {
        LOG.info("received message inventory");
        Order order = Transformers.jsonToOrder(orderStr);
        order.getItemList().forEach(orderItem -> {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                LOG.warnf("Inventory for item %s is below threshold (%d), contact supplier!",
                        orderItem.getProductId(), LOW_THRESHOLD);
            } else {
                orderItem.setQuantity(new_quantity);
            }
        });
    }
}
