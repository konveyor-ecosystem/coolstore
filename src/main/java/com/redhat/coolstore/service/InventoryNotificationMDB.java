package com.redhat.coolstore.service;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.transaction.Transactional;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/** Replaces the WebLogic t3 topic subscriber; in-memory "inventory" channel (AMQP optional). */
@ApplicationScoped
public class InventoryNotificationMDB {

    private static final Logger LOG = Logger.getLogger(InventoryNotificationMDB.class.getName());
    private static final int LOW_THRESHOLD = 50;

    @Inject
    CatalogService catalogService;

    @Incoming("inventory")
    @Blocking
    @Transactional
    public void onMessage(String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        order.getItemList().forEach(orderItem -> {
            var item = catalogService.getCatalogItemById(orderItem.getProductId());
            if (item == null || item.getInventory() == null) {
                return;
            }
            int remaining = item.getInventory().getQuantity() - orderItem.getQuantity();
            if (remaining < LOW_THRESHOLD) {
                LOG.warning("Inventory for item " + orderItem.getProductId() + " is below threshold, remaining: " + remaining);
            }
        });
    }
}
