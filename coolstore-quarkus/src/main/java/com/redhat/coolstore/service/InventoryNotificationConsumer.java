package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Replaces the original InventoryNotificationMDB which used WebLogic T3 JNDI
 * (WLInitialContextFactory, t3://localhost:7001) to connect to topic/orders.
 *
 * Now implemented as an AMQP consumer on the 'orders-inventory' channel
 * (same AMQ Artemis topic/orders, different consumer group for independent consumption).
 * Monitors inventory levels and logs low-stock alerts when quantity falls below threshold.
 */
@ApplicationScoped
public class InventoryNotificationConsumer {

    private static final Logger LOG = Logger.getLogger(InventoryNotificationConsumer.class);

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Incoming("orders-inventory")
    public void onMessage(String orderStr) {
        LOG.infof("Received inventory notification message: %s", orderStr);
        try {
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                var catalogItem = catalogService.getCatalogItemById(orderItem.getProductId());
                if (catalogItem != null && catalogItem.getInventory() != null) {
                    int oldQuantity = catalogItem.getInventory().getQuantity();
                    int newQuantity = oldQuantity - orderItem.getQuantity();
                    if (newQuantity < LOW_THRESHOLD) {
                        LOG.warnf("LOW STOCK ALERT: Inventory for item %s is below threshold (%d). " +
                                "Current quantity: %d. Contact supplier!",
                                orderItem.getProductId(), LOW_THRESHOLD, newQuantity);
                    } else {
                        LOG.infof("Inventory for item %s: %d -> %d (threshold: %d)",
                                orderItem.getProductId(), oldQuantity, newQuantity, LOW_THRESHOLD);
                    }
                } else {
                    LOG.warnf("Could not find catalog item for productId: %s", orderItem.getProductId());
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process inventory notification message: %s", orderStr);
            // Do not rethrow - inventory notification is non-critical; log and continue
        }
    }
}
